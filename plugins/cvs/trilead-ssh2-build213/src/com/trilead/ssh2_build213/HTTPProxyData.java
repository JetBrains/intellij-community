
package com.trilead.ssh2_build213;

/**
 * A <code>HTTPProxyData</code> object is used to specify the needed connection data
 * to connect through a HTTP proxy. 
 * 
 * @see Connection#setProxyData(ProxyData)
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: HTTPProxyData.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public class HTTPProxyData implements ProxyData
{
	public final String proxyHost;
	public final int proxyPort;
	public final String proxyUser;
	public final String proxyPass;
	public final String[] requestHeaderLines;

	/**
	 * Same as calling {@link #HTTPProxyData(String, int, String, String) HTTPProxyData(proxyHost, proxyPort, <code>null</code>, <code>null</code>)}
	 * 
	 * @param proxyHost Proxy hostname.
	 * @param proxyPort Proxy port.
	 */
	public HTTPProxyData(String proxyHost, int proxyPort)
	{
		this(proxyHost, proxyPort, null, null);
	}

	/**
	 * Same as calling {@link #HTTPProxyData(String, int, String, String, String[]) HTTPProxyData(proxyHost, proxyPort, <code>null</code>, <code>null</code>, <code>null</code>)}
	 *
	 * @param proxyHost Proxy hostname.
	 * @param proxyPort Proxy port.
	 * @param proxyUser Username for basic authentication (<code>null</code> if no authentication is needed).
	 * @param proxyPass Password for basic authentication (<code>null</code> if no authentication is needed).
	 */
	public HTTPProxyData(String proxyHost, int proxyPort, String proxyUser, String proxyPass)
	{
		this(proxyHost, proxyPort, proxyUser, proxyPass, null);
	}

	/**
	 * Connection data for a HTTP proxy. It is possible to specify a username and password
	 * if the proxy requires basic authentication. Also, additional request header lines can
	 * be specified (e.g., "User-Agent: CERN-LineMode/2.15 libwww/2.17b3").
	 * <p>
	 * Please note: if you want to use basic authentication, then both <code>proxyUser</code>
	 * and <code>proxyPass</code> must be non-null.
	 * <p>
	 * Here is an example:
	 * <p>
	 * <code>
	 * new HTTPProxyData("192.168.1.1", "3128", "proxyuser", "secret", new String[] {"User-Agent: TrileadBasedClient/1.0", "X-My-Proxy-Option: something"});
	 * </code>
	 * 
	 * @param proxyHost Proxy hostname.
	 * @param proxyPort Proxy port.
	 * @param proxyUser Username for basic authentication (<code>null</code> if no authentication is needed).
	 * @param proxyPass Password for basic authentication (<code>null</code> if no authentication is needed).
	 * @param requestHeaderLines An array with additional request header lines (without end-of-line markers)
	 *        that have to be sent to the server. May be <code>null</code>.
	 */

	public HTTPProxyData(String proxyHost, int proxyPort, String proxyUser, String proxyPass,
			String[] requestHeaderLines)
	{
		if (proxyHost == null)
			throw new IllegalArgumentException("proxyHost must be non-null");

		if (proxyPort < 0)
			throw new IllegalArgumentException("proxyPort must be non-negative");

		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.proxyUser = proxyUser;
		this.proxyPass = proxyPass;
		this.requestHeaderLines = requestHeaderLines;
	}
}
