
package com.trilead.ssh2_build213.channel;

/**
 * Channel.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: Channel.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class Channel
{
	/*
	 * OK. Here is an important part of the JVM Specification:
	 * (http://java.sun.com/docs/books/vmspec/2nd-edition/html/Threads.doc.html#22214)
	 * 
	 * Any association between locks and variables is purely conventional.
	 * Locking any lock conceptually flushes all variables from a thread's
	 * working memory, and unlocking any lock forces the writing out to main
	 * memory of all variables that the thread has assigned. That a lock may be
	 * associated with a particular object or a class is purely a convention.
	 * (...)
	 * 
	 * If a thread uses a particular shared variable only after locking a
	 * particular lock and before the corresponding unlocking of that same lock,
	 * then the thread will read the shared value of that variable from main
	 * memory after the lock operation, if necessary, and will copy back to main
	 * memory the value most recently assigned to that variable before the
	 * unlock operation.
	 * 
	 * This, in conjunction with the mutual exclusion rules for locks, suffices
	 * to guarantee that values are correctly transmitted from one thread to
	 * another through shared variables.
	 * 
	 * ====> Always keep that in mind when modifying the Channel/ChannelManger
	 * code.
	 * 
	 */

	static final int STATE_OPENING = 1;
	static final int STATE_OPEN = 2;
	static final int STATE_CLOSED = 4;

	static final int CHANNEL_BUFFER_SIZE = 30000;

	/*
	 * To achieve correctness, the following rules have to be respected when
	 * accessing this object:
	 */

	// These fields can always be read
	final ChannelManager cm;
	final ChannelOutputStream stdinStream;
	final ChannelInputStream stdoutStream;
	final ChannelInputStream stderrStream;

	// These two fields will only be written while the Channel is in state
	// STATE_OPENING.
	// The code makes sure that the two fields are written out when the state is
	// changing to STATE_OPEN.
	// Therefore, if you know that the Channel is in state STATE_OPEN, then you
	// can read these two fields without synchronizing on the Channel. However, make
	// sure that you get the latest values (e.g., flush caches by synchronizing on any
	// object). However, to be on the safe side, you can lock the channel.

	int localID = -1;
	int remoteID = -1;

	/*
	 * Make sure that we never send a data/EOF/WindowChange msg after a CLOSE
	 * msg.
	 * 
	 * This is a little bit complicated, but we have to do it in that way, since
	 * we cannot keep a lock on the Channel during the send operation (this
	 * would block sometimes the receiver thread, and, in extreme cases, can
	 * lead to a deadlock on both sides of the connection (senders are blocked
	 * since the receive buffers on the other side are full, and receiver
	 * threads wait for the senders to finish). It all depends on the
	 * implementation on the other side. But we cannot make any assumptions, we
	 * have to assume the worst case. Confused? Just believe me.
	 */

	/*
	 * If you send a message on a channel, then you have to aquire the
	 * "channelSendLock" and check the "closeMessageSent" flag (this variable
	 * may only be accessed while holding the "channelSendLock" !!!
	 * 
	 * BTW: NEVER EVER SEND MESSAGES FROM THE RECEIVE THREAD - see explanation
	 * above.
	 */

	final Object channelSendLock = new Object();
	boolean closeMessageSent = false;

	/*
	 * Stop memory fragmentation by allocating this often used buffer.
	 * May only be used while holding the channelSendLock
	 */

	final byte[] msgWindowAdjust = new byte[9];

	// If you access (read or write) any of the following fields, then you have
	// to synchronize on the channel.

	int state = STATE_OPENING;

	boolean closeMessageRecv = false;

	/* This is a stupid implementation. At the moment we can only wait
	 * for one pending request per channel.
	 */
	int successCounter = 0;
	int failedCounter = 0;

	int localWindow = 0; /* locally, we use a small window, < 2^31 */
	long remoteWindow = 0; /* long for readable  2^32 - 1 window support */

	int localMaxPacketSize = -1;
	int remoteMaxPacketSize = -1;

	final byte[] stdoutBuffer = new byte[CHANNEL_BUFFER_SIZE];
	final byte[] stderrBuffer = new byte[CHANNEL_BUFFER_SIZE];

	int stdoutReadpos = 0;
	int stdoutWritepos = 0;
	int stderrReadpos = 0;
	int stderrWritepos = 0;

	boolean EOF = false;

	Integer exit_status;

	String exit_signal;

	// we keep the x11 cookie so that this channel can be closed when this
	// specific x11 forwarding gets stopped

	String hexX11FakeCookie;

	// reasonClosed is special, since we sometimes need to access it
	// while holding the channelSendLock.
	// We protect it with a private short term lock.

	private final Object reasonClosedLock = new Object();
	private String reasonClosed = null;

	public Channel(ChannelManager cm)
	{
		this.cm = cm;

		this.localWindow = CHANNEL_BUFFER_SIZE;
		this.localMaxPacketSize = 35000 - 1024; // leave enough slack

		this.stdinStream = new ChannelOutputStream(this);
		this.stdoutStream = new ChannelInputStream(this, false);
		this.stderrStream = new ChannelInputStream(this, true);
	}

	/* Methods to allow access from classes outside of this package */

	public ChannelInputStream getStderrStream()
	{
		return stderrStream;
	}

	public ChannelOutputStream getStdinStream()
	{
		return stdinStream;
	}

	public ChannelInputStream getStdoutStream()
	{
		return stdoutStream;
	}

	public String getExitSignal()
	{
		synchronized (this)
		{
			return exit_signal;
		}
	}

	public Integer getExitStatus()
	{
		synchronized (this)
		{
			return exit_status;
		}
	}

	public String getReasonClosed()
	{
		synchronized (reasonClosedLock)
		{
			return reasonClosed;
		}
	}

	public void setReasonClosed(String reasonClosed)
	{
		synchronized (reasonClosedLock)
		{
			if (this.reasonClosed == null)
				this.reasonClosed = reasonClosed;
		}
	}
}
