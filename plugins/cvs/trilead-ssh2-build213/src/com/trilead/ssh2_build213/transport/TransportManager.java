
package com.trilead.ssh2_build213.transport;

import com.trilead.ssh2_build213.*;
import com.trilead.ssh2_build213.crypto.CryptoWishList;
import com.trilead.ssh2_build213.crypto.cipher.BlockCipher;
import com.trilead.ssh2_build213.crypto.digest.MAC;
import com.trilead.ssh2_build213.log.Logger;
import com.trilead.ssh2_build213.packets.PacketDisconnect;
import com.trilead.ssh2_build213.packets.Packets;
import com.trilead.ssh2_build213.packets.TypesReader;
import com.trilead.ssh2_build213.util.Tokenizer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Vector;


/*
 * Yes, the "standard" is a big mess. On one side, the say that arbitary channel
 * packets are allowed during kex exchange, on the other side we need to blindly
 * ignore the next _packet_ if the KEX guess was wrong. Where do we know from that
 * the next packet is not a channel data packet? Yes, we could check if it is in
 * the KEX range. But the standard says nothing about this. The OpenSSH guys
 * block local "normal" traffic during KEX. That's fine - however, they assume
 * that the other side is doing the same. During re-key, if they receive traffic
 * other than KEX, they become horribly irritated and kill the connection. Since
 * we are very likely going to communicate with OpenSSH servers, we have to play
 * the same game - even though we could do better.
 * 
 * btw: having stdout and stderr on the same channel, with a shared window, is
 * also a VERY good idea... =(
 */

/**
 * TransportManager.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: TransportManager.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class TransportManager
{
	private static final Logger log = Logger.getLogger(TransportManager.class);

    class HandlerEntry
	{
		MessageHandler mh;
		int low;
		int high;
	}

	private final Vector asynchronousQueue = new Vector();
	private Thread asynchronousThread = null;

	class AsynchronousWorker extends Thread
	{
		public void run()
		{
			while (true)
			{
				byte[] msg = null;

				synchronized (asynchronousQueue)
				{
					if (asynchronousQueue.size() == 0)
					{
						/* After the queue is empty for about 2 seconds, stop this thread */

						try
						{
							asynchronousQueue.wait(2000);
						}
						catch (InterruptedException e)
						{
							/* OKOK, if somebody interrupts us, then we may die earlier. */
						}

						if (asynchronousQueue.size() == 0)
						{
							asynchronousThread = null;
							return;
						}
					}

					msg = (byte[]) asynchronousQueue.remove(0);
				}

				/* The following invocation may throw an IOException.
				 * There is no point in handling it - it simply means
				 * that the connection has a problem and we should stop
				 * sending asynchronously messages. We do not need to signal that
				 * we have exited (asynchronousThread = null): further
				 * messages in the queue cannot be sent by this or any
				 * other thread.
				 * Other threads will sooner or later (when receiving or
				 * sending the next message) get the same IOException and
				 * get to the same conclusion.
				 */

				try
				{
					sendMessage(msg);
				}
				catch (IOException e)
				{
					return;
				}
			}
		}
	}

	String hostname;
	int port;
	Socket sock = new Socket();

	Object connectionSemaphore = new Object();

	boolean flagKexOngoing = false;
	boolean connectionClosed = false;

	Throwable reasonClosedCause = null;

	TransportConnection tc;
	KexManager km;

	Vector messageHandlers = new Vector();

	Thread receiveThread;

	Vector connectionMonitors = new Vector();
	boolean monitorsWereInformed = false;
    private ClientServerHello myCsh;

	/**
	 * There were reports that there are JDKs which use
	 * the resolver even though one supplies a dotted IP
	 * address in the Socket constructor. That is why we
	 * try to generate the InetAdress "by hand".
	 * 
	 * @param host
	 * @return the InetAddress
	 * @throws UnknownHostException
	 */
	static InetAddress createInetAddress(String host) throws UnknownHostException
	{
		/* Check if it is a dotted IP4 address */

		InetAddress addr = parseIPv4Address(host);

		if (addr != null)
			return addr;

		return InetAddress.getByName(host);
	}

	private static InetAddress parseIPv4Address(String host) throws UnknownHostException
	{
		if (host == null)
			return null;

		String[] quad = Tokenizer.parseTokens(host, '.');

		if ((quad == null) || (quad.length != 4))
			return null;

		byte[] addr = new byte[4];

		for (int i = 0; i < 4; i++)
		{
			int part = 0;

			if ((quad[i].length() == 0) || (quad[i].length() > 3))
				return null;

			for (int k = 0; k < quad[i].length(); k++)
			{
				char c = quad[i].charAt(k);

				/* No, Character.isDigit is not the same */
				if ((c < '0') || (c > '9'))
					return null;

				part = part * 10 + (c - '0');
			}

			if (part > 255) /* 300.1.2.3 is invalid =) */
				return null;

			addr[i] = (byte) part;
		}

		return InetAddress.getByAddress(host, addr);
	}

	public TransportManager(String host, int port) throws IOException
	{
		this.hostname = host;
		this.port = port;
	}

	public int getPacketOverheadEstimate()
	{
		return tc.getPacketOverheadEstimate();
	}

	public void setTcpNoDelay(boolean state) throws IOException
	{
		sock.setTcpNoDelay(state);
	}

	public void setSoTimeout(int timeout) throws IOException
	{
		sock.setSoTimeout(timeout);
	}

	public ConnectionInfo getConnectionInfo(int kexNumber) throws IOException
	{
		return km.getOrWaitForConnectionInfo(kexNumber);
	}

	public Throwable getReasonClosedCause()
	{
		synchronized (connectionSemaphore)
		{
			return reasonClosedCause;
		}
	}

	public byte[] getSessionIdentifier()
	{
		return km.sessionId;
	}

	public void close(Throwable cause, boolean useDisconnectPacket)
	{
		if (useDisconnectPacket == false)
		{
			/* OK, hard shutdown - do not aquire the semaphore,
			 * perhaps somebody is inside (and waits until the remote
			 * side is ready to accept new data). */

			try
			{
				sock.close();
			}
			catch (IOException ignore)
			{
			}

			/* OK, whoever tried to send data, should now agree that
			 * there is no point in further waiting =)
			 * It is safe now to aquire the semaphore.
			 */
		}

		synchronized (connectionSemaphore)
		{
			if (connectionClosed == false)
			{
				if (useDisconnectPacket == true)
				{
					try
					{
						byte[] msg = new PacketDisconnect(Packets.SSH_DISCONNECT_BY_APPLICATION, cause.getMessage(), "")
								.getPayload();
						if (tc != null)
							tc.sendMessage(msg);
					}
					catch (IOException ignore)
					{
					}

					try
					{
						sock.close();
					}
					catch (IOException ignore)
					{
					}
				}

				connectionClosed = true;
				reasonClosedCause = cause; /* may be null */
			}
			connectionSemaphore.notifyAll();
		}

		/* No check if we need to inform the monitors */

		Vector monitors = null;

		synchronized (this)
		{
			/* Short term lock to protect "connectionMonitors"
			 * and "monitorsWereInformed"
			 * (they may be modified concurrently)
			 */

			if (monitorsWereInformed == false)
			{
				monitorsWereInformed = true;
				monitors = (Vector) connectionMonitors.clone();
			}
		}

		if (monitors != null)
		{
			for (int i = 0; i < monitors.size(); i++)
			{
				try
				{
					ConnectionMonitor cmon = (ConnectionMonitor) monitors.elementAt(i);
					cmon.connectionLost(reasonClosedCause);
				}
				catch (Exception ignore)
				{
				}
			}
		}
	}

    public void initialize(CryptoWishList cwl, ServerHostKeyVerifier verifier, DHGexParameters dhgex,
			int connectTimeout, SecureRandom rnd, ProxyData proxyData) throws IOException
	{
		/* First, establish the TCP connection to the SSH-2 server */

        sock = SocketFactory.open(hostname, port, proxyData, connectTimeout);

		/* Parse the server line and say hello - important: this information is later needed for the
		 * key exchange (to stop man-in-the-middle attacks) - that is why we wrap it into an object
		 * for later use.
		 */

		myCsh = new ClientServerHello(sock.getInputStream(), sock.getOutputStream());

		tc = new TransportConnection(sock.getInputStream(), sock.getOutputStream(), rnd);

		km = new KexManager(this, myCsh, cwl, hostname, port, verifier, rnd);
		km.initiateKEX(cwl, dhgex);

		receiveThread = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					receiveLoop();
				}
				catch (IOException e)
				{
					close(e, false);

					if (log.isEnabled())
						log.log(10, "Receive thread: error in receiveLoop: " + e.getMessage());
				}

				if (log.isEnabled())
					log.log(50, "Receive thread: back from receiveLoop");

				/* Tell all handlers that it is time to say goodbye */

				if (km != null)
				{
					try
					{
						km.handleMessage(null, 0);
					}
					catch (IOException e)
					{
					}
				}

				for (int i = 0; i < messageHandlers.size(); i++)
				{
					HandlerEntry he = (HandlerEntry) messageHandlers.elementAt(i);
					try
					{
						he.mh.handleMessage(null, 0);
					}
					catch (Exception ignore)
					{
					}
				}
			}
		});

		receiveThread.setDaemon(true);
		receiveThread.start();
	}

	public void registerMessageHandler(MessageHandler mh, int low, int high)
	{
		HandlerEntry he = new HandlerEntry();
		he.mh = mh;
		he.low = low;
		he.high = high;

		synchronized (messageHandlers)
		{
			messageHandlers.addElement(he);
		}
	}

	public void removeMessageHandler(MessageHandler mh, int low, int high)
	{
		synchronized (messageHandlers)
		{
			for (int i = 0; i < messageHandlers.size(); i++)
			{
				HandlerEntry he = (HandlerEntry) messageHandlers.elementAt(i);
				if ((he.mh == mh) && (he.low == low) && (he.high == high))
				{
					messageHandlers.removeElementAt(i);
					break;
				}
			}
		}
	}

	public void sendKexMessage(byte[] msg) throws IOException
	{
		synchronized (connectionSemaphore)
		{
			if (connectionClosed)
			{
				throw (IOException) new IOException("Sorry, this connection is closed.").initCause(reasonClosedCause);
			}

			flagKexOngoing = true;

			try
			{
				tc.sendMessage(msg);
			}
			catch (IOException e)
			{
				close(e, false);
				throw e;
			}
		}
	}

	public void kexFinished() throws IOException
	{
		synchronized (connectionSemaphore)
		{
			flagKexOngoing = false;
			connectionSemaphore.notifyAll();
		}
	}

	public void forceKeyExchange(CryptoWishList cwl, DHGexParameters dhgex) throws IOException
	{
		km.initiateKEX(cwl, dhgex);
	}

	public void changeRecvCipher(BlockCipher bc, MAC mac)
	{
		tc.changeRecvCipher(bc, mac);
	}

	public void changeSendCipher(BlockCipher bc, MAC mac)
	{
		tc.changeSendCipher(bc, mac);
	}

	public void sendAsynchronousMessage(byte[] msg) throws IOException
	{
		synchronized (asynchronousQueue)
		{
			asynchronousQueue.addElement(msg);

			/* This limit should be flexible enough. We need this, otherwise the peer
			 * can flood us with global requests (and other stuff where we have to reply
			 * with an asynchronous message) and (if the server just sends data and does not
			 * read what we send) this will probably put us in a low memory situation
			 * (our send queue would grow and grow and...) */

			if (asynchronousQueue.size() > 100)
				throw new IOException("Error: the peer is not consuming our asynchronous replies.");

			/* Check if we have an asynchronous sending thread */

			if (asynchronousThread == null)
			{
				asynchronousThread = new AsynchronousWorker();
				asynchronousThread.setDaemon(true);
				asynchronousThread.start();

				/* The thread will stop after 2 seconds of inactivity (i.e., empty queue) */
			}
		}
	}

	public void setConnectionMonitors(Vector monitors)
	{
		synchronized (this)
		{
			connectionMonitors = (Vector) monitors.clone();
		}
	}

	public void sendMessage(byte[] msg) throws IOException
	{
		if (Thread.currentThread() == receiveThread)
			throw new IOException("Assertion error: sendMessage may never be invoked by the receiver thread!");

		synchronized (connectionSemaphore)
		{
			while (true)
			{
				if (connectionClosed)
				{
					throw (IOException) new IOException("Sorry, this connection is closed.")
							.initCause(reasonClosedCause);
				}

				if (flagKexOngoing == false)
					break;

				try
				{
					connectionSemaphore.wait();
				}
				catch (InterruptedException e)
				{
				}
			}

			try
			{
				tc.sendMessage(msg);
			}
			catch (IOException e)
			{
				close(e, false);
				throw e;
			}
		}
	}

	public void receiveLoop() throws IOException
	{
		byte[] msg = new byte[35000];

		while (true)
		{
			int msglen = tc.receiveMessage(msg, 0, msg.length);

			int type = msg[0] & 0xff;

			if (type == Packets.SSH_MSG_IGNORE)
				continue;

			if (type == Packets.SSH_MSG_DEBUG)
			{
				if (log.isEnabled())
				{
					TypesReader tr = new TypesReader(msg, 0, msglen);
					tr.readByte();
					tr.readBoolean();
					StringBuffer debugMessageBuffer = new StringBuffer();
					debugMessageBuffer.append(tr.readString("UTF-8"));

					for (int i = 0; i < debugMessageBuffer.length(); i++)
					{
						char c = debugMessageBuffer.charAt(i);

						if ((c >= 32) && (c <= 126))
							continue;
						debugMessageBuffer.setCharAt(i, '\uFFFD');
					}

					log.log(50, "DEBUG Message from remote: '" + debugMessageBuffer.toString() + "'");
				}
				continue;
			}

			if (type == Packets.SSH_MSG_UNIMPLEMENTED)
			{
				throw new IOException("Peer sent UNIMPLEMENTED message, that should not happen.");
			}

			if (type == Packets.SSH_MSG_DISCONNECT)
			{
				TypesReader tr = new TypesReader(msg, 0, msglen);
				tr.readByte();
				int reason_code = tr.readUINT32();
				StringBuffer reasonBuffer = new StringBuffer();
				reasonBuffer.append(tr.readString("UTF-8"));

				/*
				 * Do not get fooled by servers that send abnormal long error
				 * messages
				 */

				if (reasonBuffer.length() > 255)
				{
					reasonBuffer.setLength(255);
					reasonBuffer.setCharAt(254, '.');
					reasonBuffer.setCharAt(253, '.');
					reasonBuffer.setCharAt(252, '.');
				}

				/*
				 * Also, check that the server did not send charcaters that may
				 * screw up the receiver -> restrict to reasonable US-ASCII
				 * subset -> "printable characters" (ASCII 32 - 126). Replace
				 * all others with 0xFFFD (UNICODE replacement character).
				 */

				for (int i = 0; i < reasonBuffer.length(); i++)
				{
					char c = reasonBuffer.charAt(i);

					if ((c >= 32) && (c <= 126))
						continue;
					reasonBuffer.setCharAt(i, '\uFFFD');
				}

				throw new IOException("Peer sent DISCONNECT message (reason code " + reason_code + "): "
						+ reasonBuffer.toString());
			}

			/*
			 * Is it a KEX Packet?
			 */

			if ((type == Packets.SSH_MSG_KEXINIT) || (type == Packets.SSH_MSG_NEWKEYS)
					|| ((type >= 30) && (type <= 49)))
			{
				km.handleMessage(msg, msglen);
				continue;
			}

			MessageHandler mh = null;

			for (int i = 0; i < messageHandlers.size(); i++)
			{
				HandlerEntry he = (HandlerEntry) messageHandlers.elementAt(i);
				if ((he.low <= type) && (type <= he.high))
				{
					mh = he.mh;
					break;
				}
			}

			if (mh == null)
				throw new IOException("Unexpected SSH message (type " + type + ")");

			mh.handleMessage(msg, msglen);
		}
	}

    public ClientServerHello getClientServerHello() {
        return myCsh;
    }
}
