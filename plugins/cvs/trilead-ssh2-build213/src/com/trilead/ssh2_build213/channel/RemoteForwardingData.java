
package com.trilead.ssh2_build213.channel;

/**
 * RemoteForwardingData. Data about a requested remote forwarding.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: RemoteForwardingData.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class RemoteForwardingData
{
	public String bindAddress;
	public int bindPort;

	String targetAddress;
	int targetPort;
}
