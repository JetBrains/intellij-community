import com.trilead.ssh2_build213.ChannelCondition;
import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.Session;

import java.io.IOException;
import java.io.InputStream;

/**
 * This example shows how to use the Session.waitForCondition
 * method to implement a state machine approach for
 * proper stdout/stderr output handling in a single thread.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SingleThreadStdoutStderr.java,v 1.6 2007/10/15 12:49:57 cplattne Exp $
 */
public class SingleThreadStdoutStderr
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

			sess.execCommand("echo \"Huge amounts of text on STDOUT\"; echo \"Huge amounts of text on STDERR\" >&2");

			/*
			 * Advanced:
			 * The following is a demo on how one can read from stdout and
			 * stderr without having to use two parallel worker threads (i.e.,
			 * we don't use the Streamgobblers here) and at the same time not
			 * risking a deadlock (due to a filled SSH2 channel window, caused
			 * by the stream which you are currently NOT reading from =).
			 */

			/* Don't wrap these streams and don't let other threads work on
			 * these streams while you work with Session.waitForCondition()!!!
			 */

			InputStream stdout = sess.getStdout();
			InputStream stderr = sess.getStderr();

			byte[] buffer = new byte[8192];

			while (true)
			{
				if ((stdout.available() == 0) && (stderr.available() == 0))
				{
					/* Even though currently there is no data available, it may be that new data arrives
					 * and the session's underlying channel is closed before we call waitForCondition().
					 * This means that EOF and STDOUT_DATA (or STDERR_DATA, or both) may
					 * be set together.
					 */

					int conditions = sess.waitForCondition(ChannelCondition.STDOUT_DATA | ChannelCondition.STDERR_DATA
							| ChannelCondition.EOF, 2000);

					/* Wait no longer than 2 seconds (= 2000 milliseconds) */

					if ((conditions & ChannelCondition.TIMEOUT) != 0)
					{
						/* A timeout occured. */
						throw new IOException("Timeout while waiting for data from peer.");
					}

					/* Here we do not need to check separately for CLOSED, since CLOSED implies EOF */

					if ((conditions & ChannelCondition.EOF) != 0)
					{
						/* The remote side won't send us further data... */

						if ((conditions & (ChannelCondition.STDOUT_DATA | ChannelCondition.STDERR_DATA)) == 0)
						{
							/* ... and we have consumed all data in the local arrival window. */
							break;
						}
					}

					/* OK, either STDOUT_DATA or STDERR_DATA (or both) is set. */

					// You can be paranoid and check that the library is not going nuts:
					// if ((conditions & (ChannelCondition.STDOUT_DATA | ChannelCondition.STDERR_DATA)) == 0)
					//	throw new IllegalStateException("Unexpected condition result (" + conditions + ")");
				}

				/* If you below replace "while" with "if", then the way the output appears on the local
				 * stdout and stder streams is more "balanced". Addtionally reducing the buffer size
				 * will also improve the interleaving, but performance will slightly suffer.
				 * OKOK, that all matters only if you get HUGE amounts of stdout and stderr data =)
				 */

				while (stdout.available() > 0)
				{
					int len = stdout.read(buffer);
					if (len > 0) // this check is somewhat paranoid
						System.out.write(buffer, 0, len);
				}

				while (stderr.available() > 0)
				{
					int len = stderr.read(buffer);
					if (len > 0) // this check is somewhat paranoid
						System.err.write(buffer, 0, len);
				}
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
