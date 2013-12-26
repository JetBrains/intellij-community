import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.Session;
import com.trilead.ssh2_build213.StreamGobbler;

import java.io.*;

/**
 * This example shows how to login using
 * public key authentication.
 *  
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PublicKeyAuthentication.java,v 1.2 2007/10/15 12:49:57 cplattne Exp $
 */
public class PublicKeyAuthentication
{
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

			/* Create a session */

			Session sess = conn.openSession();

			sess.execCommand("uname -a && date && uptime && who");

			InputStream stdout = new StreamGobbler(sess.getStdout());

			BufferedReader br = new BufferedReader(new InputStreamReader(stdout));

			System.out.println("Here is some information about the remote host:");

			while (true)
			{
				String line = br.readLine();
				if (line == null)
					break;
				System.out.println(line);
			}

			/* Close this session */

			sess.close();

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
