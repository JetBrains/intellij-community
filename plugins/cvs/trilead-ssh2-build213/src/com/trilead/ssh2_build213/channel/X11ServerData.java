
package com.trilead.ssh2_build213.channel;

/**
 * X11ServerData. Data regarding an x11 forwarding target.
 *
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: X11ServerData.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 * 
 */
public class X11ServerData
{
	public String hostname;
	public int port;
	public byte[] x11_magic_cookie; /* not the remote (fake) one, the local (real) one */
}
