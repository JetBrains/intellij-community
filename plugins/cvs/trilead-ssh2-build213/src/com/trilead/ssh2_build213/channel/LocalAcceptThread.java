
package com.trilead.ssh2_build213.channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * LocalAcceptThread.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: LocalAcceptThread.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class LocalAcceptThread extends Thread implements IChannelWorkerThread
{
	ChannelManager cm;
	String host_to_connect;
	int port_to_connect;

	final ServerSocket ss;

	public LocalAcceptThread(ChannelManager cm, int local_port, String host_to_connect, int port_to_connect)
			throws IOException
	{
		this.cm = cm;
		this.host_to_connect = host_to_connect;
		this.port_to_connect = port_to_connect;

		ss = new ServerSocket(local_port);
	}

	public LocalAcceptThread(ChannelManager cm, InetSocketAddress localAddress, String host_to_connect,
			int port_to_connect) throws IOException
	{
		this.cm = cm;
		this.host_to_connect = host_to_connect;
		this.port_to_connect = port_to_connect;

		ss = new ServerSocket();
		ss.bind(localAddress);
	}

	public void run()
	{
		try
		{
			cm.registerThread(this);
		}
		catch (IOException e)
		{
			stopWorking();
			return;
		}

		while (true)
		{
			Socket s = null;

			try
			{
				s = ss.accept();
			}
			catch (IOException e)
			{
				stopWorking();
				return;
			}

			Channel cn = null;
			StreamForwarder r2l = null;
			StreamForwarder l2r = null;

			try
			{
				/* This may fail, e.g., if the remote port is closed (in optimistic terms: not open yet) */

				cn = cm.openDirectTCPIPChannel(host_to_connect, port_to_connect, s.getInetAddress().getHostAddress(), s
						.getPort());

			}
			catch (IOException e)
			{
				/* Simply close the local socket and wait for the next incoming connection */

				try
				{
					s.close();
				}
				catch (IOException ignore)
				{
				}

				continue;
			}

			try
			{
				r2l = new StreamForwarder(cn, null, null, cn.stdoutStream, s.getOutputStream(), "RemoteToLocal");
				l2r = new StreamForwarder(cn, r2l, s, s.getInputStream(), cn.stdinStream, "LocalToRemote");
			}
			catch (IOException e)
			{
				try
				{
					/* This message is only visible during debugging, since we discard the channel immediatelly */
					cn.cm.closeChannel(cn, "Weird error during creation of StreamForwarder (" + e.getMessage() + ")",
							true);
				}
				catch (IOException ignore)
				{
				}

				continue;
			}

			r2l.setDaemon(true);
			l2r.setDaemon(true);
			r2l.start();
			l2r.start();
		}
	}

	public void stopWorking()
	{
		try
		{
			/* This will lead to an IOException in the ss.accept() call */
			ss.close();
		}
		catch (IOException e)
		{
		}
	}
}
