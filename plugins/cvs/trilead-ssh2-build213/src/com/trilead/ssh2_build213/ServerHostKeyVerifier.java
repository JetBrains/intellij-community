
package com.trilead.ssh2_build213;

/**
 * A callback interface used to implement a client specific method of checking
 * server host keys.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: ServerHostKeyVerifier.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public interface ServerHostKeyVerifier
{
	/**
	 * The actual verifier method, it will be called by the key exchange code
	 * on EVERY key exchange - this can happen several times during the lifetime
	 * of a connection.
	 * <p>
	 * Note: SSH-2 servers are allowed to change their hostkey at ANY time.
	 * 
	 * @param hostname the hostname used to create the {@link Connection} object
	 * @param port the remote TCP port
	 * @param serverHostKeyAlgorithm the public key algorithm (<code>ssh-rsa</code> or <code>ssh-dss</code>)
	 * @param serverHostKey the server's public key blob
	 * @return if the client wants to accept the server's host key - if not, the
	 *         connection will be closed.
	 * @throws Exception Will be wrapped with an IOException, extended version of returning false =)
	 */
	public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey)
			throws Exception;
}
