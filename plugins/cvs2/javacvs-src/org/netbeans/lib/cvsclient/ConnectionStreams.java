/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.file.IReaderFactory;
import org.netbeans.lib.cvsclient.file.IWriterFactory;
import org.netbeans.lib.cvsclient.io.*;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author  Thomas Singer
 */
public final class ConnectionStreams
        implements IConnectionStreams, IReaderFactory, IWriterFactory {

	// Fields =================================================================

	private final IConnection connection;
	private final IStreamLogger streamLogger;

	private InputStream loggedInputStream;
	private OutputStream loggedOutputStream;
	private Reader loggedReader;
	private Writer loggedWriter;
	private InputStream inputStream;
	private OutputStream outputStream;
	private Reader reader;
	private Writer writer;
	private DeflaterOutputStream deflaterOutputStream;
	private boolean utf8;

	// Setup ==================================================================

	public ConnectionStreams(IConnection connection, IStreamLogger streamLogger) {
		this(connection, streamLogger, false);
	}

	public ConnectionStreams(IConnection connection, IStreamLogger streamLogger, boolean utf8) {
		BugLog.getInstance().assertNotNull(connection);
		BugLog.getInstance().assertNotNull(streamLogger);

		this.connection = connection;
		this.streamLogger = streamLogger;
		this.utf8 = utf8;

		setInputStream(connection.getInputStream());
		setOutputStream(connection.getOutputStream());
	}

	// Implemented ============================================================

	public Reader createReader(InputStream inputStream) {
		if (utf8) {
            return new Utf8InputStreamReader(inputStream);
		}
		else {
			return new AsciiInputStreamReader(inputStream);
		}
	}

	public Writer createWriter(OutputStream outputStream) {
		if (utf8) {
            return new Utf8OutputStreamWriter(outputStream);
		}
		else {
			return new AsciiOutputStreamWriter(outputStream);
		}
	}

	// Accessing ==============================================================

	public IReaderFactory getReaderFactory() {
		return this;
	}

	public IWriterFactory getWriterFactory() {
		return this;
	}

	public InputStream getLoggedInputStream() {
		return loggedInputStream;
	}

	public OutputStream getLoggedOutputStream() {
		return loggedOutputStream;
	}

	public Reader getLoggedReader() {
		return loggedReader;
	}

	public Writer getLoggedWriter() {
		return loggedWriter;
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public Reader getReader() {
		return reader;
	}

	public Writer getWriter() {
		return writer;
	}

	public void flushForReading() throws IOException {
		loggedWriter.flush();
		if (deflaterOutputStream != null) {
			deflaterOutputStream.finish();

			println("@until here the content is gzipped@", streamLogger.getOutputLogStream());
		}
		loggedOutputStream.flush();
	}

	public void close() {
		try {
			if (loggedOutputStream != null) {
				try {
					loggedOutputStream.close();
				}
				catch (IOException ex) {
					BugLog.getInstance().showException(ex);
				}
			}

			if (loggedInputStream != null) {
				try {
					loggedInputStream.close();
				}
				catch (IOException ex) {
					BugLog.getInstance().showException(ex);
				}
			}
		}
		finally {
			try {
				connection.close();
			}
			catch (IOException ex) {
				BugLog.getInstance().showException(ex);
			}
		}
	}

	// Actions ================================================================

	public void setGzipped() throws IOException {
		loggedWriter.flush();
		loggedOutputStream.flush();

		println("@from now on the content is gzipped@", streamLogger.getInputLogStream());
		println("@from now on the content is gzipped@", streamLogger.getOutputLogStream());

		deflaterOutputStream = new DeflaterOutputStream(connection.getOutputStream(), new Deflater(6));
		setOutputStream(deflaterOutputStream);

		setInputStream(new InflaterInputStream(connection.getInputStream()));
	}

	public void setUtf8() {
		this.utf8 = true;
	}

	// Utils ==================================================================

	private void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
		this.reader = createReader(inputStream);

		this.loggedInputStream = streamLogger.createLoggingInputStream(inputStream);
		this.loggedReader = createReader(this.loggedInputStream);
	}

	private void setOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;
		this.writer = createWriter(outputStream);

		this.loggedOutputStream = streamLogger.createLoggingOutputStream(outputStream);
		this.loggedWriter = createWriter(this.loggedOutputStream);
	}

	private void println(String text, OutputStream outputStream) throws IOException {
		final OutputStreamWriter writerNoSpecialEncoding = new OutputStreamWriter(outputStream);
		println(text, writerNoSpecialEncoding);
		writerNoSpecialEncoding.flush();
	}

	private void println(String text, Writer writer) throws IOException {
		writer.write(text);
		writer.write('\n');
	}
}