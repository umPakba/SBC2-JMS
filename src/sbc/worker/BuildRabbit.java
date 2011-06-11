package sbc.worker;

import java.util.Random;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

import sbc.model.ChocolateRabbit;
import sbc.model.Egg;
import sbc.model.Nest;


public class BuildRabbit extends Worker {

	public static void main(String[] args)	{
		BuildRabbit rab = new BuildRabbit(args);
	}

	private static Logger log = Logger.getLogger(BuildRabbit.class);

	private final static String messageSelectorALL = "product = 'egg' OR product = 'chocolateRabbit'"; 
	private final static String messageSelectorCHOCO = "product = 'chocolateRabbit'"; 
	private final static String messageSelectorEGG = "product = 'egg'"; 

	private static String consumerName = "sbc.build.queue";

	private MessageConsumer consumer;

	private Nest currentNest;
	private Queue consumerQueue;

	private int chocoCount;

	private int eggCount;

	private boolean close;


	public BuildRabbit(String[] args)	{
		super(args);
		currentNest = null;
		eggCount = chocoCount = 0;
		this.addShutdownHook();
		this.initConsumer();
	}

	/**
	 * shutdown hook
	 */
	private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	log.info("SHUTDOWN...");
            	close();
            }
        });
	}

	@Override
	protected void initConsumer() {
		try {
			consumerQueue = (Queue) ctx.lookup(consumerName);

			consumer = session.createConsumer(consumerQueue, messageSelectorALL);

		} catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.receiveMessages();

		log.info("#######################################");
		log.info("###### BUILD RABBIT waiting for incrediants");
		log.info("###### shutdown using Ctrl + C");
		log.info("#######################################");
	}

	public void receiveMessages() {

		Message message = null;

		while(!close)	{

			try {
				message = consumer.receive(3000);
			} catch (JMSException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			if(message == null)
				continue;

			if(currentNest == null)	{
				currentNest = new Nest(this.id);
				chocoCount = eggCount = 0;
			}

			if(message instanceof ObjectMessage)	{
				ObjectMessage om = (ObjectMessage) message;
				try {

					// stop consumer
					consumer.close();

					if(om.getObject() instanceof Egg)	{

						log.info("###### received 1 EGG");

						if(eggCount < 2)	{

							log.info("###### add 1 EGG to nest");

							currentNest.addEgg((Egg) om.getObject());
							eggCount++;
						}
					} else if(om.getObject() instanceof ChocolateRabbit)	{

						log.info("###### received 1 ChocoRabbit");

						if(chocoCount == 0)	{

							log.info("###### add 1 CHOCORABBIT to nest");

							currentNest.setRabbit((ChocolateRabbit) om.getObject());
							chocoCount++;
						}
					} else	{
						// ERROR
						log.error("ERROR: NO EGG or RABBIT GIVEN");
						return;
					}

					int sleep = new Random().nextInt(3) + 1;

					Thread.sleep(sleep * 1000);

					log.info("NEST STATUS: " + currentNest);

					// change messageSelector
					// send nest im completed
					if(currentNest.isComplete())	{
						// send nest
						log.info("###### NEST is complete, send it");
						log.info("#######################################");

						ObjectMessage replyMsg = session.createObjectMessage(currentNest);

						producer.send(replyMsg);

						currentNest = null;
						eggCount = chocoCount = 0;

						consumer = session.createConsumer(consumerQueue, messageSelectorALL);
						log.info("###### waiting for incrediants...");
						log.info("#######################################");

					} else	{
						if(eggCount < 2 && chocoCount == 0)	{
							consumer = session.createConsumer(consumerQueue, messageSelectorALL);
						} else if(eggCount >= 2 && chocoCount == 0)	{
							consumer = session.createConsumer(consumerQueue, messageSelectorCHOCO);
						} else	{
							consumer = session.createConsumer(consumerQueue, messageSelectorEGG);
						}
					}

				} catch (JMSException e) {
					close = true;
				} catch (InterruptedException e) {
					close = true;
				}
			}
		}
		this.close();
	}


	@Override
	protected void close() {
		close = true;
		System.out.println("SHUTTING DOWN...");
		if(currentNest != null)	{
			ObjectMessage replyMsg;
			if(currentNest.getEgg1() != null)	{
				try {
					replyMsg = session.createObjectMessage(currentNest.getEgg1());
					replyMsg.setBooleanProperty("hideFromGUI", true);
					producer.send(replyMsg);
				} catch (JMSException e) {
				}
			}
			if(currentNest.getEgg2() != null)	{
				try {
					replyMsg = session.createObjectMessage(currentNest.getEgg2());
					replyMsg.setBooleanProperty("hideFromGUI", true);
					producer.send(replyMsg);
				} catch (JMSException e) {
				}
			}
			if(currentNest.getRabbit() != null)	{
				try {
					replyMsg = session.createObjectMessage(currentNest.getRabbit());
					replyMsg.setBooleanProperty("hideFromGUI", true);
					producer.send(replyMsg);
				} catch (JMSException e) {
				}
			}
		}
		try {
			producer.close();
			consumer.setMessageListener(null);
			consumer.close();
			session.close();
			connection.stop();
			connection.close();
			ctx.close();
			ctx = null;
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		} finally	{
			System.exit(0);
		}
	}
}