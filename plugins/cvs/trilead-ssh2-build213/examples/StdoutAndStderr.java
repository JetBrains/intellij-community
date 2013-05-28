import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.Session;
import com.trilead.ssh2_build213.StreamGobbler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This example shows how to consume stdout/stderr output
 * using two StreamGobblers. This is simpler to program
 * than the state machine approach (see SingleThreadStdoutStderr.java),
 * but you cannot control the amount of memory that is
 * consumed by your application (i.e., in case the other
 * side sends you lots of data).
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: StdoutAndStderr.java,v 1.2 2007/10/15 12:49:57 cplattne Exp $
 */
public class StdoutAndStderr
{
	public static void main(String[] args)
	{
		String hostname = "127.0.0.1";
		String username = "joe";
		String password = "joespass";

		try
		{
			/* Create a connection instance */

			Connection conn = new Connection(hostname);

			/* Now connect */

			conn.connect();

			/* Authenticate */

			boolean isAuthenticated = conn.authenticateWithPassword(username, password);

			if (isAuthenticated == false)
				throw new IOException("Authentication failed.");

			/* Create a session */

			Session sess = conn.openSession();

			sess.execCommand("echo \"Text on STDOUT\"; echo \"Text on STDERR\" >&2");

			InputStream stdout = new StreamGobbler(sess.getStdout());
			InputStream stderr = new StreamGobbler(sess.getStderr());

			BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
			BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));

			System.out.println("Here is the output from stdout:");

			while (true)
			{
				String line = stdoutReader.readLine();
				if (line == null)
					break;
				System.out.println(line);
			}

			System.out.println("Here is the output from stderr:");

			while (true)
			{
				String line = stderrReader.readLine();
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
