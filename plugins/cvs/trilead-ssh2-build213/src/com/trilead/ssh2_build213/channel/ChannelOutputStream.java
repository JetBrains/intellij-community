package com.trilead.ssh2_build213.channel;

import java.io.IOException;
import java.io.OutputStream;

/**
 * ChannelOutputStream.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: ChannelOutputStream.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public final class ChannelOutputStream extends OutputStream
{
	Channel c;

	boolean isClosed = false;
	
	ChannelOutputStream(Channel c)
	{
		this.c = c;
	}

	public void write(int b) throws IOException
	{	
		byte[] buff = new byte[1];
		
		buff[0] = (byte) b;
		
		write(buff, 0, 1);
	}

	public void close() throws IOException
	{
		if (isClosed == false)
		{
			isClosed = true;
			c.cm.sendEOF(c);
		}
	}

	public void flush() throws IOException
	{
		if (isClosed)
			throw new IOException("This OutputStream is closed.");

		/* This is a no-op, since this stream is unbuffered */
	}

	public void write(byte[] b, int off, int len) throws IOException
	{
		if (isClosed)
			throw new IOException("This OutputStream is closed.");
		
		if (b == null)
			throw new NullPointerException();

		if ((off < 0) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0) || (off > b.length))
			throw new IndexOutOfBoundsException();

		if (len == 0)
			return;
		
		c.cm.sendData(c, b, off, len);
	}

	public void write(byte[] b) throws IOException
	{
		write(b, 0, b.length);
	}
}
