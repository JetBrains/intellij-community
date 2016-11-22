
package com.trilead.ssh2_build213.util;

import com.trilead.ssh2_build213.log.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedList;


/**
 * TimeoutService (beta). Here you can register a timeout.
 * <p>
 * Implemented having large scale programs in mind: if you open many concurrent SSH connections
 * that rely on timeouts, then there will be only one timeout thread. Once all timeouts
 * have expired/are cancelled, the thread will (sooner or later) exit.
 * Only after new timeouts arrive a new thread (singleton) will be instantiated.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: TimeoutService.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class TimeoutService
{
	private static final Logger log = Logger.getLogger(TimeoutService.class);

	public static class TimeoutToken implements Comparable
	{
		private long runTime;
		private Runnable handler;

		private TimeoutToken(long runTime, Runnable handler)
		{
			this.runTime = runTime;
			this.handler = handler;
		}

		public int compareTo(Object o)
		{
			TimeoutToken t = (TimeoutToken) o;
			if (runTime > t.runTime)
				return 1;
			if (runTime == t.runTime)
				return 0;
			return -1;
		}
	}

	private static class TimeoutThread extends Thread
	{
		public void run()
		{
			synchronized (todolist)
			{
				while (true)
				{
					if (todolist.size() == 0)
					{
						timeoutThread = null;
						return;
					}

					long now = System.currentTimeMillis();

					TimeoutToken tt = (TimeoutToken) todolist.getFirst();

					if (tt.runTime > now)
					{
						/* Not ready yet, sleep a little bit */

						try
						{
							todolist.wait(tt.runTime - now);
						}
						catch (InterruptedException e)
						{
						}

						/* We cannot simply go on, since it could be that the token
						 * was removed (cancelled) or another one has been inserted in
						 * the meantime.
						 */

						continue;
					}

					todolist.removeFirst();

					try
					{
						tt.handler.run();
					}
					catch (Exception e)
					{
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						log.log(20, "Exeception in Timeout handler:" + e.getMessage() + "(" + sw.toString() + ")");
					}
				}
			}
		}
	}

	/* The list object is also used for locking purposes */
	private static final LinkedList todolist = new LinkedList();

	private static Thread timeoutThread = null;

	/**
	 * It is assumed that the passed handler will not execute for a long time.
	 * 
	 * @param runTime
	 * @param handler
	 * @return a TimeoutToken that can be used to cancel the timeout.
	 */
	public static final TimeoutToken addTimeoutHandler(long runTime, Runnable handler)
	{
		TimeoutToken token = new TimeoutToken(runTime, handler);

		synchronized (todolist)
		{
			todolist.add(token);
			Collections.sort(todolist);

			if (timeoutThread != null)
				timeoutThread.interrupt();
			else
			{
				timeoutThread = new TimeoutThread();
				timeoutThread.setDaemon(true);
				timeoutThread.start();
			}
		}

		return token;
	}

	public static final void cancelTimeoutHandler(TimeoutToken token)
	{
		synchronized (todolist)
		{
			todolist.remove(token);

			if (timeoutThread != null)
				timeoutThread.interrupt();
		}
	}

}
