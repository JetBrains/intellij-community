
package com.trilead.ssh2_build213;

/**
 * Contains constants that can be used to specify what conditions to wait for on
 * a SSH-2 channel (e.g., represented by a {@link Session}).
 *
 * @see Session#waitForCondition(int, long)
 *
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: ChannelCondition.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public abstract interface ChannelCondition
{
	/**
	 * A timeout has occurred, none of your requested conditions is fulfilled.
	 * However, other conditions may be true - therefore, NEVER use the "=="
	 * operator to test for this (or any other) condition. Always use
	 * something like <code>((cond & ChannelCondition.CLOSED) != 0)</code>.
	 */
	public static final int TIMEOUT = 1;

	/**
	 * The underlying SSH-2 channel, however not necessarily the whole connection,
	 * has been closed. This implies <code>EOF</code>. Note that there may still
	 * be unread stdout or stderr data in the local window, i.e, <code>STDOUT_DATA</code>
	 * or/and <code>STDERR_DATA</code> may be set at the same time.
	 */
	public static final int CLOSED = 2;

	/**
	 * There is stdout data available that is ready to be consumed.
	 */
	public static final int STDOUT_DATA = 4;

	/**
	 * There is stderr data available that is ready to be consumed.
	 */
	public static final int STDERR_DATA = 8;

	/**
	 * EOF on has been reached, no more _new_ stdout or stderr data will arrive
	 * from the remote server. However, there may be unread stdout or stderr
	 * data, i.e, <code>STDOUT_DATA</code> or/and <code>STDERR_DATA</code>
	 * may be set at the same time.
	 */
	public static final int EOF = 16;

	/**
	 * The exit status of the remote process is available.
	 * Some servers never send the exist status, or occasionally "forget" to do so.
	 */
	public static final int EXIT_STATUS = 32;

	/**
	 * The exit signal of the remote process is available.
	 */
	public static final int EXIT_SIGNAL = 64;

}
