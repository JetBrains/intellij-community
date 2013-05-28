
package com.trilead.ssh2_build213.channel;

import com.trilead.ssh2_build213.log.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


/**
 * RemoteX11AcceptThread.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: RemoteX11AcceptThread.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class RemoteX11AcceptThread extends Thread
{
	private static final Logger log = Logger.getLogger(RemoteX11AcceptThread.class);

	Channel c;

	String remoteOriginatorAddress;
	int remoteOriginatorPort;

	Socket s;

	public RemoteX11AcceptThread(Channel c, String remoteOriginatorAddress, int remoteOriginatorPort)
	{
		this.c = c;
		this.remoteOriginatorAddress = remoteOriginatorAddress;
		this.remoteOriginatorPort = remoteOriginatorPort;
	}

	public void run()
	{
		try
		{
			/* Send Open Confirmation */

			c.cm.sendOpenConfirmation(c);

			/* Read startup packet from client */

			OutputStream remote_os = c.getStdinStream();
			InputStream remote_is = c.getStdoutStream();

			/* The following code is based on the protocol description given in:
			 * Scheifler/Gettys,
			 * X Windows System: Core and Extension Protocols:
			 * X Version 11, Releases 6 and 6.1 ISBN 1-55558-148-X
			 */

			/*
			 * Client startup:
			 * 
			 * 1 0X42 MSB first/0x6c lSB first - byteorder
			 * 1 - unused
			 * 2 card16 - protocol-major-version
			 * 2 card16 - protocol-minor-version
			 * 2 n - lenght of authorization-protocol-name
			 * 2 d - lenght of authorization-protocol-data
			 * 2 - unused
			 * string8 - authorization-protocol-name
			 * p - unused, p=pad(n)
			 * string8 - authorization-protocol-data
			 * q - unused, q=pad(d)
			 * 
			 * pad(X) = (4 - (X mod 4)) mod 4
			 * 
			 * Server response:
			 * 
			 * 1 (0 failed, 2 authenticate, 1 success)
			 * ...
			 * 
			 */

			/* Later on we will simply forward the first 6 header bytes to the "real" X11 server */

			byte[] header = new byte[6];

			if (remote_is.read(header) != 6)
				throw new IOException("Unexpected EOF on X11 startup!");

			if ((header[0] != 0x42) && (header[0] != 0x6c)) // 0x42 MSB first, 0x6C LSB first
				throw new IOException("Unknown endian format in X11 message!");

			/* Yes, I came up with this myself - shall I file an application for a patent? =) */
			
			int idxMSB = (header[0] == 0x42) ? 0 : 1;

			/* Read authorization data header */

			byte[] auth_buff = new byte[6];

			if (remote_is.read(auth_buff) != 6)
				throw new IOException("Unexpected EOF on X11 startup!");

			int authProtocolNameLength = ((auth_buff[idxMSB] & 0xff) << 8) | (auth_buff[1 - idxMSB] & 0xff);
			int authProtocolDataLength = ((auth_buff[2 + idxMSB] & 0xff) << 8) | (auth_buff[3 - idxMSB] & 0xff);

			if ((authProtocolNameLength > 256) || (authProtocolDataLength > 256))
				throw new IOException("Buggy X11 authorization data");

			int authProtocolNamePadding = ((4 - (authProtocolNameLength % 4)) % 4);
			int authProtocolDataPadding = ((4 - (authProtocolDataLength % 4)) % 4);

			byte[] authProtocolName = new byte[authProtocolNameLength];
			byte[] authProtocolData = new byte[authProtocolDataLength];

			byte[] paddingBuffer = new byte[4];

			if (remote_is.read(authProtocolName) != authProtocolNameLength)
				throw new IOException("Unexpected EOF on X11 startup! (authProtocolName)");

			if (remote_is.read(paddingBuffer, 0, authProtocolNamePadding) != authProtocolNamePadding)
				throw new IOException("Unexpected EOF on X11 startup! (authProtocolNamePadding)");

			if (remote_is.read(authProtocolData) != authProtocolDataLength)
				throw new IOException("Unexpected EOF on X11 startup! (authProtocolData)");

			if (remote_is.read(paddingBuffer, 0, authProtocolDataPadding) != authProtocolDataPadding)
				throw new IOException("Unexpected EOF on X11 startup! (authProtocolDataPadding)");

			if ("MIT-MAGIC-COOKIE-1".equals(new String(authProtocolName, "ISO-8859-1")) == false)
				throw new IOException("Unknown X11 authorization protocol!");

			if (authProtocolDataLength != 16)
				throw new IOException("Wrong data length for X11 authorization data!");

			StringBuffer tmp = new StringBuffer(32);
			for (int i = 0; i < authProtocolData.length; i++)
			{
				String digit2 = Integer.toHexString(authProtocolData[i] & 0xff);
				tmp.append((digit2.length() == 2) ? digit2 : "0" + digit2);
			}
			String hexEncodedFakeCookie = tmp.toString();

			/* Order is very important here - it may be that a certain x11 forwarding
			 * gets disabled right in the moment when we check and register our connection
			 * */

			synchronized (c)
			{
				/* Please read the comment in Channel.java */
				c.hexX11FakeCookie = hexEncodedFakeCookie;
			}

			/* Now check our fake cookie directory to see if we produced this cookie */

			X11ServerData sd = c.cm.checkX11Cookie(hexEncodedFakeCookie);

			if (sd == null)
				throw new IOException("Invalid X11 cookie received.");

			/* If the session which corresponds to this cookie is closed then we will
			 * detect this: the session's close code will close all channels
			 * with the session's assigned x11 fake cookie.
			 */

			s = new Socket(sd.hostname, sd.port);

			OutputStream x11_os = s.getOutputStream();
			InputStream x11_is = s.getInputStream();

			/* Now we are sending the startup packet to the real X11 server */

			x11_os.write(header);

			if (sd.x11_magic_cookie == null)
			{
				byte[] emptyAuthData = new byte[6];
				/* empty auth data, hopefully you are connecting to localhost =) */
				x11_os.write(emptyAuthData);
			}
			else
			{
				if (sd.x11_magic_cookie.length != 16)
					throw new IOException("The real X11 cookie has an invalid length!");

				/* send X11 cookie specified by client */
				x11_os.write(auth_buff);
				x11_os.write(authProtocolName); /* re-use */
				x11_os.write(paddingBuffer, 0, authProtocolNamePadding);
				x11_os.write(sd.x11_magic_cookie);
				x11_os.write(paddingBuffer, 0, authProtocolDataPadding);
			}

			x11_os.flush();

			/* Start forwarding traffic */

			StreamForwarder r2l = new StreamForwarder(c, null, null, remote_is, x11_os, "RemoteToX11");
			StreamForwarder l2r = new StreamForwarder(c, null, null, x11_is, remote_os, "X11ToRemote");

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

			c.cm.closeChannel(c, "EOF on both X11 streams reached.", true);
			s.close();
		}
		catch (IOException e)
		{
			log.log(50, "IOException in X11 proxy code: " + e.getMessage());

			try
			{
				c.cm.closeChannel(c, "IOException in X11 proxy code (" + e.getMessage() + ")", true);
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
