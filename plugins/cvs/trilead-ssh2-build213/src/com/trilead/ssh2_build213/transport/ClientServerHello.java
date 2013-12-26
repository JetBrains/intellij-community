
package com.trilead.ssh2_build213.transport;

import com.trilead.ssh2_build213.Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * ClientServerHello.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: ClientServerHello.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class ClientServerHello
{
	String server_line;
	String client_line;

	String server_versioncomment;

	public final static int readLineRN(InputStream is, byte[] buffer) throws IOException
	{
		int pos = 0;
		boolean need10 = false;
		int len = 0;
		while (true)
		{
			int c = is.read();
			if (c == -1)
				throw new IOException("Premature connection close");

			buffer[pos++] = (byte) c;

			if (c == 13)
			{
				need10 = true;
				continue;
			}

			if (c == 10)
				break;

			if (need10 == true)
				throw new IOException("Malformed line sent by the server, the line does not end correctly.");

			len++;
			if (pos >= buffer.length)
				throw new IOException("The server sent a too long line.");
		}

		return len;
	}

	public ClientServerHello(InputStream bi, OutputStream bo) throws IOException
	{
		client_line = "SSH-2.0-" + Connection.identification;

		bo.write((client_line + "\r\n").getBytes("ISO-8859-1"));
		bo.flush();

		byte[] serverVersion = new byte[512];

		for (int i = 0; i < 50; i++)
		{
			int len = readLineRN(bi, serverVersion);

			server_line = new String(serverVersion, 0, len, "ISO-8859-1");

			if (server_line.startsWith("SSH-"))
				break;
		}

		if (server_line.startsWith("SSH-") == false)
			throw new IOException(
					"Malformed server identification string. There was no line starting with 'SSH-' amongst the first 50 lines.");

		if (server_line.startsWith("SSH-1.99-"))
			server_versioncomment = server_line.substring(9);
		else if (server_line.startsWith("SSH-2.0-"))
			server_versioncomment = server_line.substring(8);
		else
			throw new IOException("Server uses incompatible protocol, it is not SSH-2 compatible.");
	}

	/**
	 * @return Returns the client_versioncomment.
	 */
	public byte[] getClientString()
	{
		byte[] result;

		try
		{
			result = client_line.getBytes("ISO-8859-1");
		}
		catch (UnsupportedEncodingException ign)
		{
			result = client_line.getBytes();
		}

		return result;
	}

	/**
	 * @return Returns the server_versioncomment.
	 */
	public byte[] getServerString()
	{
		byte[] result;

		try
		{
			result = server_line.getBytes("ISO-8859-1");
		}
		catch (UnsupportedEncodingException ign)
		{
			result = server_line.getBytes();
		}

		return result;
	}
}
