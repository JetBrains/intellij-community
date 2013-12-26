import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.LocalPortForwarder;

import java.io.File;
import java.io.IOException;

/**
 * This example shows how to deal with port forwardings.
 *  
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PortForwarding.java,v 1.2 2007/10/15 12:49:57 cplattne Exp $
 */
public class PortForwarding
{
	public static void sleepSomeTime(long milliSeconds)
	{
		try
		{
			Thread.sleep(milliSeconds);
		}
		catch (InterruptedException e)
		{
		}
	}

	public static void main(String[] args)
	{
		String hostname = "127.0.0.1";
		String username = "joe";

		File keyfile = new File("~/.ssh/id_rsa"); // or "~/.ssh/id_dsa"
		String keyfilePass = "joespass"; // will be ignored if not needed

		try
		{
			/* Create a connection instance */

			Connection conn = new Connection(hostname);

			/* Now connect */

			conn.connect();

			/* Authenticate */

			boolean isAuthenticated = conn.authenticateWithPublicKey(username, keyfile, keyfilePass);

			if (isAuthenticated == false)
				throw new IOException("Authentication failed.");

			/* ===== OK, now let's establish some local port forwardings ===== */

			/* Example Port Forwarding: -L 8080:www.icann.org:80 (OpenSSH notation)
			 * 
			 * This works by allocating a socket to listen on 8080 on the local interface (127.0.0.1).
			 * Whenever a connection is made to this port (127.0.0.1:8080), the connection is forwarded
			 * over the secure channel, and a connection is made to www.icann.org:80 from the remote
			 * machine (i.e., the ssh server).
			 * 
			 * (the above text is based partially on the OpenSSH man page)
			 */

			/* You can create as many of them as you want */

			LocalPortForwarder lpf1 = conn.createLocalPortForwarder(8080, "www.icann.org", 80);

			/* Now simply point your webbrowser to 127.0.0.1:8080 */
			/* (on the host where you execute this program)                         */

			/* ===== OK, now let's establish some remote port forwardings ===== */

			/* Example Port Forwarding: -R 127.0.0.1:8080:www.ripe.net:80 (OpenSSH notation)
			 * 
			 * Specifies that the port 127.0.0.1:8080 on the remote server is to be forwarded to the
			 * given host and port on the local side.  This works by allocating a socket to listen to port
			 * 8080 on the remote side (the ssh server), and whenever a connection is made to this port, the
			 * connection is forwarded over the secure channel, and a connection is made to
			 * www.ripe.net:80 by the Trilead SSH-2 library.
			 * 
			 * (the above text is based partially on the OpenSSH man page)
			 */

			/* You can create as many of them as you want */

			conn.requestRemotePortForwarding("127.0.0.1", 8080, "www.ripe.net", 80);

			/* Now, on the ssh server, if you connect to 127.0.0.1:8080, then the connection is forwarded
			 * through the secure tunnel to the library, which in turn will forward the connection
			 * to www.ripe.net:80. */

			/* Sleep a bit... (30 seconds) */
			sleepSomeTime(30000);

			/* Stop accepting remote connections that are being forwarded to www.ripe.net:80 */

			conn.cancelRemotePortForwarding(8080);

			/* Sleep a bit... (20 seconds) */
			sleepSomeTime(20000);

			/* Stop accepting connections on 127.0.0.1:8080 that are being forwarded to www.icann.org:80 */

			lpf1.close();

			/* Close the connection */

			conn.close();

		}
		catch (IOException e)
		{
			e.printStackTrace(System.err);
			System.exit(2);
		}
	}
}
