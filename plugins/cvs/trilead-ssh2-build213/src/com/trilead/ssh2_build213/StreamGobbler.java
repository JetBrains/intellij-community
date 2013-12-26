
package com.trilead.ssh2_build213;

import java.io.IOException;
import java.io.InputStream;

/**
 * A <code>StreamGobbler</code> is an InputStream that uses an internal worker
 * thread to constantly consume input from another InputStream. It uses a buffer
 * to store the consumed data. The buffer size is automatically adjusted, if needed.
 * <p>
 * This class is sometimes very convenient - if you wrap a session's STDOUT and STDERR
 * InputStreams with instances of this class, then you don't have to bother about
 * the shared window of STDOUT and STDERR in the low level SSH-2 protocol,
 * since all arriving data will be immediatelly consumed by the worker threads.
 * Also, as a side effect, the streams will be buffered (e.g., single byte
 * read() operations are faster).
 * <p>
 * Other SSH for Java libraries include this functionality by default in
 * their STDOUT and STDERR InputStream implementations, however, please be aware
 * that this approach has also a downside:
 * <p>
 * If you do not call the StreamGobbler's <code>read()</code> method often enough
 * and the peer is constantly sending huge amounts of data, then you will sooner or later
 * encounter a low memory situation due to the aggregated data (well, it also depends on the Java heap size).
 * Joe Average will like this class anyway - a paranoid programmer would never use such an approach.
 * <p>
 * The term "StreamGobbler" was taken from an article called "When Runtime.exec() won't",
 * see http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: StreamGobbler.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public class StreamGobbler extends InputStream
{
	class GobblerThread extends Thread
	{
		public void run()
		{
			byte[] buff = new byte[8192];

			while (true)
			{
				try
				{
					int avail = is.read(buff);

					synchronized (synchronizer)
					{
						if (avail <= 0)
						{
							isEOF = true;
							synchronizer.notifyAll();
							break;
						}
						
						int space_available = buffer.length - write_pos;
						
						if (space_available < avail)
						{
							/* compact/resize buffer */

							int unread_size = write_pos - read_pos;
							int need_space = unread_size + avail;

							byte[] new_buffer = buffer;

							if (need_space > buffer.length)
							{
								int inc = need_space / 3;
								inc = (inc < 256) ? 256 : inc;
								inc = (inc > 8192) ? 8192 : inc;
								new_buffer = new byte[need_space + inc];
							}
							
							if (unread_size > 0)
								System.arraycopy(buffer, read_pos, new_buffer, 0, unread_size);

							buffer = new_buffer;
							
							read_pos = 0;
							write_pos = unread_size;
						}
						
						System.arraycopy(buff, 0, buffer, write_pos, avail);
						write_pos += avail;

						synchronizer.notifyAll();
					}	
				}
				catch (IOException e)
				{
					synchronized (synchronizer)
					{
						exception = e;
						synchronizer.notifyAll();
						break;
					}
				}
			}
		}
	}

	private InputStream is;
	private GobblerThread t;

	private Object synchronizer = new Object();

	private boolean isEOF = false;
	private boolean isClosed = false;
	private IOException exception = null;

	private byte[] buffer = new byte[2048];
	private int read_pos = 0;
	private int write_pos = 0;

	public StreamGobbler(InputStream is)
	{
		this.is = is;
		t = new GobblerThread();
		t.setDaemon(true);
		t.start();
	}

	public int read() throws IOException
	{
		synchronized (synchronizer)
		{
			if (isClosed)
				throw new IOException("This StreamGobbler is closed.");

			while (read_pos == write_pos)
			{
				if (exception != null)
					throw exception;

				if (isEOF)
					return -1;

				try
				{
					synchronizer.wait();
				}
				catch (InterruptedException e)
				{
				}
			}

			int b = buffer[read_pos++] & 0xff;

			return b;
		}
	}

	public int available() throws IOException
	{
		synchronized (synchronizer)
		{
			if (isClosed)
				throw new IOException("This StreamGobbler is closed.");

			return write_pos - read_pos;
		}
	}

	public int read(byte[] b) throws IOException
	{
		return read(b, 0, b.length);
	}

	public void close() throws IOException
	{
		synchronized (synchronizer)
		{
			if (isClosed)
				return;
			isClosed = true;
			isEOF = true;
			synchronizer.notifyAll();
			is.close();
		}
	}

	public int read(byte[] b, int off, int len) throws IOException
	{
		if (b == null)
			throw new NullPointerException();

		if ((off < 0) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0) || (off > b.length))
			throw new IndexOutOfBoundsException();

		if (len == 0)
			return 0;

		synchronized (synchronizer)
		{
			if (isClosed)
				throw new IOException("This StreamGobbler is closed.");

			while (read_pos == write_pos)
			{
				if (exception != null)
					throw exception;

				if (isEOF)
					return -1;

				try
				{
					synchronizer.wait();
				}
				catch (InterruptedException e)
				{
				}
			}

			int avail = write_pos - read_pos;

			avail = (avail > len) ? len : avail;

			System.arraycopy(buffer, read_pos, b, off, avail);

			read_pos += avail;

			return avail;
		}
	}
}
