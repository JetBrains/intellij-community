
package com.trilead.ssh2_build213.channel;

import com.trilead.ssh2_build213.log.Logger;

import java.io.IOException;
import java.net.Socket;


/**
 * RemoteAcceptThread.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: RemoteAcceptThread.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class RemoteAcceptThread extends Thread
{
	private static final Logger log = Logger.getLogger(RemoteAcceptThread.class);

	Channel c;

	String remoteConnectedAddress;
	int remoteConnectedPort;
	String remoteOriginatorAddress;
	int remoteOriginatorPort;
	String targetAddress;
	int targetPort;

	Socket s;

	public RemoteAcceptThread(Channel c, String remoteConnectedAddress, int remoteConnectedPort,
			String remoteOriginatorAddress, int remoteOriginatorPort, String targetAddress, int targetPort)
	{
		this.c = c;
		this.remoteConnectedAddress = remoteConnectedAddress;
		this.remoteConnectedPort = remoteConnectedPort;
		this.remoteOriginatorAddress = remoteOriginatorAddress;
		this.remoteOriginatorPort = remoteOriginatorPort;
		this.targetAddress = targetAddress;
		this.targetPort = targetPort;

		if (log.isEnabled())
			log.log(20, "RemoteAcceptThread: " + remoteConnectedAddress + "/" + remoteConnectedPort + ", R: "
					+ remoteOriginatorAddress + "/" + remoteOriginatorPort);
	}

	public void run()
	{
		try
		{
			c.cm.sendOpenConfirmation(c);

			s = new Socket(targetAddress, targetPort);

			StreamForwarder r2l = new StreamForwarder(c, null, null, c.getStdoutStream(), s.getOutputStream(),
					"RemoteToLocal");
			StreamForwarder l2r = new StreamForwarder(c, null, null, s.getInputStream(), c.getStdinStream(),
					"LocalToRemote");

			/* No need to start two threads, one can be executed in the current thread */
			
			r2l.setDaemon(true);
			r2l.start();
			l2r.run();

			while (r2l.isAlive())
			{
				try
				{
					r2l.join();
				}
				catch (InterruptedException e)
				{
				}
			}

			/* If the channel is already closed, then this is a no-op */

			c.cm.closeChannel(c, "EOF on both streams reached.", true);
			s.close();
		}
		catch (IOException e)
		{
			log.log(50, "IOException in proxy code: " + e.getMessage());

			try
			{
				c.cm.closeChannel(c, "IOException in proxy code (" + e.getMessage() + ")", true);
			}
			catch (IOException e1)
			{
			}
			try
			{
				if (s != null)
					s.close();
			}
			catch (IOException e1)
			{
			}
		}
	}
}
