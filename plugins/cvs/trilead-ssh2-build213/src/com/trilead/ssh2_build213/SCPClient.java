
package com.trilead.ssh2_build213;

import java.io.*;

/**
 * A very basic <code>SCPClient</code> that can be used to copy files from/to
 * the SSH-2 server. On the server side, the "scp" program must be in the PATH.
 * <p>
 * This scp client is thread safe - you can download (and upload) different sets
 * of files concurrently without any troubles. The <code>SCPClient</code> is
 * actually mapping every request to a distinct {@link Session}.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SCPClient.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */

public class SCPClient
{
	Connection conn;

	class LenNamePair
	{
		long length;
		String filename;
	}

	public SCPClient(Connection conn)
	{
		if (conn == null)
			throw new IllegalArgumentException("Cannot accept null argument!");
		this.conn = conn;
	}

	private void readResponse(InputStream is) throws IOException
	{
		int c = is.read();

		if (c == 0)
			return;

		if (c == -1)
			throw new IOException("Remote scp terminated unexpectedly.");

		if ((c != 1) && (c != 2))
			throw new IOException("Remote scp sent illegal error code.");

		if (c == 2)
			throw new IOException("Remote scp terminated with error.");

		String err = receiveLine(is);
		throw new IOException("Remote scp terminated with error (" + err + ").");
	}

	private String receiveLine(InputStream is) throws IOException
	{
		StringBuffer sb = new StringBuffer(30);

		while (true)
		{
			/*
			 * This is a random limit - if your path names are longer, then
			 * adjust it
			 */

			if (sb.length() > 8192)
				throw new IOException("Remote scp sent a too long line");

			int c = is.read();

			if (c < 0)
				throw new IOException("Remote scp terminated unexpectedly.");

			if (c == '\n')
				break;

			sb.append((char) c);

		}
		return sb.toString();
	}

	private LenNamePair parseCLine(String line) throws IOException
	{
		/* Minimum line: "xxxx y z" ---> 8 chars */

		long len;

		if (line.length() < 8)
			throw new IOException("Malformed C line sent by remote SCP binary, line too short.");

		if ((line.charAt(4) != ' ') || (line.charAt(5) == ' '))
			throw new IOException("Malformed C line sent by remote SCP binary.");

		int length_name_sep = line.indexOf(' ', 5);

		if (length_name_sep == -1)
			throw new IOException("Malformed C line sent by remote SCP binary.");

		String length_substring = line.substring(5, length_name_sep);
		String name_substring = line.substring(length_name_sep + 1);

		if ((length_substring.length() <= 0) || (name_substring.length() <= 0))
			throw new IOException("Malformed C line sent by remote SCP binary.");

		if ((6 + length_substring.length() + name_substring.length()) != line.length())
			throw new IOException("Malformed C line sent by remote SCP binary.");

		try
		{
			len = Long.parseLong(length_substring);
		}
		catch (NumberFormatException e)
		{
			throw new IOException("Malformed C line sent by remote SCP binary, cannot parse file length.");
		}

		if (len < 0)
			throw new IOException("Malformed C line sent by remote SCP binary, illegal file length.");

		LenNamePair lnp = new LenNamePair();
		lnp.length = len;
		lnp.filename = name_substring;

		return lnp;
	}

	private void sendBytes(Session sess, byte[] data, String fileName, String mode) throws IOException
	{
		OutputStream os = sess.getStdin();
		InputStream is = new BufferedInputStream(sess.getStdout(), 512);

		readResponse(is);

		String cline = "C" + mode + " " + data.length + " " + fileName + "\n";

		os.write(cline.getBytes("ISO-8859-1"));
		os.flush();

		readResponse(is);

		os.write(data, 0, data.length);
		os.write(0);
		os.flush();

		readResponse(is);

		os.write("E\n".getBytes("ISO-8859-1"));
		os.flush();
	}

	private void sendFiles(Session sess, String[] files, String[] remoteFiles, String mode) throws IOException
	{
		byte[] buffer = new byte[8192];

		OutputStream os = new BufferedOutputStream(sess.getStdin(), 40000);
		InputStream is = new BufferedInputStream(sess.getStdout(), 512);

		readResponse(is);

		for (int i = 0; i < files.length; i++)
		{
			File f = new File(files[i]);
			long remain = f.length();

			String remoteName;

			if ((remoteFiles != null) && (remoteFiles.length > i) && (remoteFiles[i] != null))
				remoteName = remoteFiles[i];
			else
				remoteName = f.getName();

			String cline = "C" + mode + " " + remain + " " + remoteName + "\n";

			os.write(cline.getBytes("ISO-8859-1"));
			os.flush();

			readResponse(is);

			FileInputStream fis = null;

			try
			{
				fis = new FileInputStream(f);

				while (remain > 0)
				{
					int trans;
					if (remain > buffer.length)
						trans = buffer.length;
					else
						trans = (int) remain;

					if (fis.read(buffer, 0, trans) != trans)
						throw new IOException("Cannot read enough from local file " + files[i]);

					os.write(buffer, 0, trans);

					remain -= trans;
				}
			}
			finally
			{
				if (fis != null)
					fis.close();
			}

			os.write(0);
			os.flush();

			readResponse(is);
		}

		os.write("E\n".getBytes("ISO-8859-1"));
		os.flush();
	}

	private void receiveFiles(Session sess, OutputStream[] targets) throws IOException
	{
		byte[] buffer = new byte[8192];

		OutputStream os = new BufferedOutputStream(sess.getStdin(), 512);
		InputStream is = new BufferedInputStream(sess.getStdout(), 40000);

		os.write(0x0);
		os.flush();

		for (int i = 0; i < targets.length; i++)
		{
			LenNamePair lnp = null;

			while (true)
			{
				int c = is.read();
				if (c < 0)
					throw new IOException("Remote scp terminated unexpectedly.");

				String line = receiveLine(is);

				if (c == 'T')
				{
					/* Ignore modification times */

					continue;
				}

				if ((c == 1) || (c == 2))
					throw new IOException("Remote SCP error: " + line);

				if (c == 'C')
				{
					lnp = parseCLine(line);
					break;

				}
				throw new IOException("Remote SCP error: " + ((char) c) + line);
			}

			os.write(0x0);
			os.flush();

			long remain = lnp.length;

			while (remain > 0)
			{
				int trans;
				if (remain > buffer.length)
					trans = buffer.length;
				else
					trans = (int) remain;

				int this_time_received = is.read(buffer, 0, trans);

				if (this_time_received < 0)
				{
					throw new IOException("Remote scp terminated connection unexpectedly");
				}

				targets[i].write(buffer, 0, this_time_received);

				remain -= this_time_received;
			}

			readResponse(is);

			os.write(0x0);
			os.flush();
		}
	}

	private void receiveFiles(Session sess, String[] files, String target) throws IOException
	{
		byte[] buffer = new byte[8192];

		OutputStream os = new BufferedOutputStream(sess.getStdin(), 512);
		InputStream is = new BufferedInputStream(sess.getStdout(), 40000);

		os.write(0x0);
		os.flush();

		for (int i = 0; i < files.length; i++)
		{
			LenNamePair lnp = null;

			while (true)
			{
				int c = is.read();
				if (c < 0)
					throw new IOException("Remote scp terminated unexpectedly.");

				String line = receiveLine(is);

				if (c == 'T')
				{
					/* Ignore modification times */

					continue;
				}

				if ((c == 1) || (c == 2))
					throw new IOException("Remote SCP error: " + line);

				if (c == 'C')
				{
					lnp = parseCLine(line);
					break;

				}
				throw new IOException("Remote SCP error: " + ((char) c) + line);
			}

			os.write(0x0);
			os.flush();

			File f = new File(target + File.separatorChar + lnp.filename);
			FileOutputStream fop = null;

			try
			{
				fop = new FileOutputStream(f);

				long remain = lnp.length;

				while (remain > 0)
				{
					int trans;
					if (remain > buffer.length)
						trans = buffer.length;
					else
						trans = (int) remain;

					int this_time_received = is.read(buffer, 0, trans);

					if (this_time_received < 0)
					{
						throw new IOException("Remote scp terminated connection unexpectedly");
					}

					fop.write(buffer, 0, this_time_received);

					remain -= this_time_received;
				}
			}
			finally
			{
				if (fop != null)
					fop.close();
			}

			readResponse(is);

			os.write(0x0);
			os.flush();
		}
	}

	/**
	 * Copy a local file to a remote directory, uses mode 0600 when creating the
	 * file on the remote side.
	 * 
	 * @param localFile
	 *            Path and name of local file.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * 
	 * @throws IOException
	 */
	public void put(String localFile, String remoteTargetDirectory) throws IOException
	{
		put(new String[] { localFile }, remoteTargetDirectory, "0600");
	}

	/**
	 * Copy a set of local files to a remote directory, uses mode 0600 when
	 * creating files on the remote side.
	 * 
	 * @param localFiles
	 *            Paths and names of local file names.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * 
	 * @throws IOException
	 */

	public void put(String[] localFiles, String remoteTargetDirectory) throws IOException
	{
		put(localFiles, remoteTargetDirectory, "0600");
	}

	/**
	 * Copy a local file to a remote directory, uses the specified mode when
	 * creating the file on the remote side.
	 * 
	 * @param localFile
	 *            Path and name of local file.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * @param mode
	 *            a four digit string (e.g., 0644, see "man chmod", "man open")
	 * @throws IOException
	 */
	public void put(String localFile, String remoteTargetDirectory, String mode) throws IOException
	{
		put(new String[] { localFile }, remoteTargetDirectory, mode);
	}

	/**
	 * Copy a local file to a remote directory, uses the specified mode and
	 * remote filename when creating the file on the remote side.
	 * 
	 * @param localFile
	 *            Path and name of local file.
	 * @param remoteFileName
	 *            The name of the file which will be created in the remote
	 *            target directory.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * @param mode
	 *            a four digit string (e.g., 0644, see "man chmod", "man open")
	 * @throws IOException
	 */
	public void put(String localFile, String remoteFileName, String remoteTargetDirectory, String mode)
			throws IOException
	{
		put(new String[] { localFile }, new String[] { remoteFileName }, remoteTargetDirectory, mode);
	}

	/**
	 * Create a remote file and copy the contents of the passed byte array into
	 * it. Uses mode 0600 for creating the remote file.
	 * 
	 * @param data
	 *            the data to be copied into the remote file.
	 * @param remoteFileName
	 *            The name of the file which will be created in the remote
	 *            target directory.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * @throws IOException
	 */

	public void put(byte[] data, String remoteFileName, String remoteTargetDirectory) throws IOException
	{
		put(data, remoteFileName, remoteTargetDirectory, "0600");
	}

	/**
	 * Create a remote file and copy the contents of the passed byte array into
	 * it. The method use the specified mode when creating the file on the
	 * remote side.
	 * 
	 * @param data
	 *            the data to be copied into the remote file.
	 * @param remoteFileName
	 *            The name of the file which will be created in the remote
	 *            target directory.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * @param mode
	 *            a four digit string (e.g., 0644, see "man chmod", "man open")
	 * @throws IOException
	 */
	public void put(byte[] data, String remoteFileName, String remoteTargetDirectory, String mode) throws IOException
	{
		Session sess = null;

		if ((remoteFileName == null) || (remoteTargetDirectory == null) || (mode == null))
			throw new IllegalArgumentException("Null argument.");

		if (mode.length() != 4)
			throw new IllegalArgumentException("Invalid mode.");

		for (int i = 0; i < mode.length(); i++)
			if (Character.isDigit(mode.charAt(i)) == false)
				throw new IllegalArgumentException("Invalid mode.");

		remoteTargetDirectory = remoteTargetDirectory.trim();
		remoteTargetDirectory = (remoteTargetDirectory.length() > 0) ? remoteTargetDirectory : ".";

		String cmd = "scp -t -d " + remoteTargetDirectory;

		try
		{
			sess = conn.openSession();
			sess.execCommand(cmd);
			sendBytes(sess, data, remoteFileName, mode);
		}
		catch (IOException e)
		{
			throw (IOException) new IOException("Error during SCP transfer.").initCause(e);
		}
		finally
		{
			if (sess != null)
				sess.close();
		}
	}

	/**
	 * Copy a set of local files to a remote directory, uses the specified mode
	 * when creating the files on the remote side.
	 * 
	 * @param localFiles
	 *            Paths and names of the local files.
	 * @param remoteTargetDirectory
	 *            Remote target directory. Use an empty string to specify the
	 *            default directory.
	 * @param mode
	 *            a four digit string (e.g., 0644, see "man chmod", "man open")
	 * @throws IOException
	 */
	public void put(String[] localFiles, String remoteTargetDirectory, String mode) throws IOException
	{
		put(localFiles, null, remoteTargetDirectory, mode);
	}

	public void put(String[] localFiles, String[] remoteFiles, String remoteTargetDirectory, String mode)
			throws IOException
	{
		Session sess = null;

		/*
		 * remoteFiles may be null, indicating that the local filenames shall be
		 * used
		 */

		if ((localFiles == null) || (remoteTargetDirectory == null) || (mode == null))
			throw new IllegalArgumentException("Null argument.");

		if (mode.length() != 4)
			throw new IllegalArgumentException("Invalid mode.");

		for (int i = 0; i < mode.length(); i++)
			if (Character.isDigit(mode.charAt(i)) == false)
				throw new IllegalArgumentException("Invalid mode.");

		if (localFiles.length == 0)
			return;

		remoteTargetDirectory = remoteTargetDirectory.trim();
		remoteTargetDirectory = (remoteTargetDirectory.length() > 0) ? remoteTargetDirectory : ".";

		String cmd = "scp -t -d " + remoteTargetDirectory;

		for (int i = 0; i < localFiles.length; i++)
		{
			if (localFiles[i] == null)
				throw new IllegalArgumentException("Cannot accept null filename.");
		}

		try
		{
			sess = conn.openSession();
			sess.execCommand(cmd);
			sendFiles(sess, localFiles, remoteFiles, mode);
		}
		catch (IOException e)
		{
			throw (IOException) new IOException("Error during SCP transfer.").initCause(e);
		}
		finally
		{
			if (sess != null)
				sess.close();
		}
	}

	/**
	 * Download a file from the remote server to a local directory.
	 * 
	 * @param remoteFile
	 *            Path and name of the remote file.
	 * @param localTargetDirectory
	 *            Local directory to put the downloaded file.
	 * 
	 * @throws IOException
	 */
	public void get(String remoteFile, String localTargetDirectory) throws IOException
	{
		get(new String[] { remoteFile }, localTargetDirectory);
	}

	/**
	 * Download a file from the remote server and pipe its contents into an
	 * <code>OutputStream</code>. Please note that, to enable flexible usage
	 * of this method, the <code>OutputStream</code> will not be closed nor
	 * flushed.
	 * 
	 * @param remoteFile
	 *            Path and name of the remote file.
	 * @param target
	 *            OutputStream where the contents of the file will be sent to.
	 * @throws IOException
	 */
	public void get(String remoteFile, OutputStream target) throws IOException
	{
		get(new String[] { remoteFile }, new OutputStream[] { target });
	}

	private void get(String remoteFiles[], OutputStream[] targets) throws IOException
	{
		Session sess = null;

		if ((remoteFiles == null) || (targets == null))
			throw new IllegalArgumentException("Null argument.");

		if (remoteFiles.length != targets.length)
			throw new IllegalArgumentException("Length of arguments does not match.");

		if (remoteFiles.length == 0)
			return;

		String cmd = "scp -f";

		for (int i = 0; i < remoteFiles.length; i++)
		{
			if (remoteFiles[i] == null)
				throw new IllegalArgumentException("Cannot accept null filename.");

			String tmp = remoteFiles[i].trim();

			if (tmp.length() == 0)
				throw new IllegalArgumentException("Cannot accept empty filename.");

			cmd += (" " + tmp);
		}

		try
		{
			sess = conn.openSession();
			sess.execCommand(cmd);
			receiveFiles(sess, targets);
		}
		catch (IOException e)
		{
			throw (IOException) new IOException("Error during SCP transfer.").initCause(e);
		}
		finally
		{
			if (sess != null)
				sess.close();
		}
	}

	/**
	 * Download a set of files from the remote server to a local directory.
	 * 
	 * @param remoteFiles
	 *            Paths and names of the remote files.
	 * @param localTargetDirectory
	 *            Local directory to put the downloaded files.
	 * 
	 * @throws IOException
	 */
	public void get(String remoteFiles[], String localTargetDirectory) throws IOException
	{
		Session sess = null;

		if ((remoteFiles == null) || (localTargetDirectory == null))
			throw new IllegalArgumentException("Null argument.");

		if (remoteFiles.length == 0)
			return;

		String cmd = "scp -f";

		for (int i = 0; i < remoteFiles.length; i++)
		{
			if (remoteFiles[i] == null)
				throw new IllegalArgumentException("Cannot accept null filename.");

			String tmp = remoteFiles[i].trim();

			if (tmp.length() == 0)
				throw new IllegalArgumentException("Cannot accept empty filename.");

			cmd += (" " + tmp);
		}

		try
		{
			sess = conn.openSession();
			sess.execCommand(cmd);
			receiveFiles(sess, remoteFiles, localTargetDirectory);
		}
		catch (IOException e)
		{
			throw (IOException) new IOException("Error during SCP transfer.").initCause(e);
		}
		finally
		{
			if (sess != null)
				sess.close();
		}
	}
}
