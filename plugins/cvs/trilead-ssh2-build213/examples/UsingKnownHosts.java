import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.KnownHosts;
import com.trilead.ssh2_build213.Session;
import com.trilead.ssh2_build213.StreamGobbler;

import java.io.*;

/**
 * This example shows how to deal with "known_hosts" files.
 *  
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: UsingKnownHosts.java,v 1.2 2007/10/15 12:49:57 cplattne Exp $
 */
public class UsingKnownHosts
{
	static KnownHosts database = new KnownHosts();

	public static void main(String[] args) throws IOException
	{
		String hostname = "somehost";
		String username = "joe";
		String password = "joespass";

		File knownHosts = new File("~/.ssh/known_hosts");

		try
		{
			/* Load known_hosts file into in-memory database */

			if (knownHosts.exists())
				database.addHostkeys(knownHosts);

			/* Create a connection instance */

			Connection conn = new Connection(hostname);

			/* Now connect and use the SimpleVerifier */

			conn.connect(new SimpleVerifier(database));

			/* Authenticate */

			boolean isAuthenticated = conn.authenticateWithPassword(username, password);

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
