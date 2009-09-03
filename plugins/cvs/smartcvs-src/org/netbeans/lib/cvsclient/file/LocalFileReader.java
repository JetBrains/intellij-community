package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.SmartCvsSrcBundle;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.Collection;

/**
 * @author  Thomas Singer
 */
public final class LocalFileReader
        implements ILocalFileReader {

	// Constants ==============================================================

	/**
	 * The size of chunks read from disk.
	 */
	private static final int CHUNK_SIZE = 32768;

	// Fields =================================================================

	private final ISendTextFilePreprocessor sendTextFilePreprocessor;
  @NonNls private static final String CVS_DIR = "CVS";

  // Setup ==================================================================

	public LocalFileReader(ISendTextFilePreprocessor sendTextFilePreprocessor) {
		BugLog.getInstance().assertNotNull(sendTextFilePreprocessor);

		this.sendTextFilePreprocessor = sendTextFilePreprocessor;
	}

	// Implemented ============================================================

	/**
	 * Transmit a text file to the server, using the standard CVS protocol
	 * conventions. CR/LFs are converted to the Unix format.
	 */
	public void transmitTextFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException {
		final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
		if (!file.exists()) {
			throw new FileNotFoundException(
                          SmartCvsSrcBundle.message("file.does.not.exist.error.message", file.getAbsolutePath()));
		}

		final File fileToSend = sendTextFilePreprocessor.getPreprocessedTextFile(file, connectionStreams.getWriterFactory());

		// first write the length of the file
		long length = fileToSend.length();
		writeLengthString(connectionStreams.getLoggedWriter(), length);

		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(fileToSend));

			final OutputStream outputStream = connectionStreams.getOutputStream();

			// now transmit the file itself
			final byte[] chunk = new byte[CHUNK_SIZE];
			while (length > 0) {
				final int bytesToRead = (length >= CHUNK_SIZE)
				        ? CHUNK_SIZE
				        : (int)length;
				final int count = bis.read(chunk, 0, bytesToRead);
				length -= count;
				outputStream.write(chunk, 0, count);
			}
			outputStream.flush();
		}
		finally {
			if (bis != null) {
				try {
					bis.close();
				}
				catch (IOException ex) {
					// ignore
				}
			}

			sendTextFilePreprocessor.cleanup(fileToSend);
		}
	}

	/**
	 * Transmit a binary file to the server, using the standard CVS protocol
	 * conventions.
	 */
	public void transmitBinaryFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException {
		final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);

		// first write the length of the file
		long length = file.length();
		writeLengthString(connectionStreams.getLoggedWriter(), length);

		final BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
		try {
			final OutputStream outputStream = connectionStreams.getOutputStream();

			// now transmit the file itself
			final byte[] chunk = new byte[CHUNK_SIZE];
			while (length > 0) {
				final int bytesToRead = (length >= CHUNK_SIZE)
				        ? CHUNK_SIZE
				        : (int)length;
				final int count = bis.read(chunk, 0, bytesToRead);
				length -= count;
				outputStream.write(chunk, 0, count);
			}
			outputStream.flush();
		}
		finally {
			try {
				bis.close();
			}
			catch (IOException ex) {
				// ignore
			}
		}
	}

	public boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
		return cvsFileSystem.getLocalFileSystem().getFile(fileObject).exists();
	}

	public boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
		return cvsFileSystem.getLocalFileSystem().getFile(fileObject).canWrite();
	}

	public void listFilesAndDirectories(DirectoryObject directoryObject, Collection fileNames, Collection directoryNames, ICvsFileSystem cvsFileSystem) {
		final File directory = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
		final File[] filesAndDirectories = directory.listFiles();
		if (filesAndDirectories == null) {
			return;
		}

		for (int i = 0; i < filesAndDirectories.length; i++) {
			final File fileOrDirectory = filesAndDirectories[i];
			final String name = fileOrDirectory.getName();
			if (name.equals(CVS_DIR)) {
				continue;
			}

			if (fileOrDirectory.isDirectory()) {
				if (directoryNames != null) {
					directoryNames.add(name);
				}
			}
			else {
				if (fileNames != null) {
					fileNames.add(name);
				}
			}
		}
	}

	// Accessing ==============================================================

	private void writeLengthString(final Writer writer, long length) throws IOException {
		writer.write(String.valueOf(length));
		writer.write('\n');
		writer.flush();
	}
}
