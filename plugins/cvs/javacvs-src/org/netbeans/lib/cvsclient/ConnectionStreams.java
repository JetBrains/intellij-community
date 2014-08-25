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
import org.netbeans.lib.cvsclient.io.IStreamLogger;
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
	private DeflaterOutputStream deflaterOutputStream;
	private final String myCharset;

  // Setup ==================================================================

	public ConnectionStreams(IConnection connection, IStreamLogger streamLogger, String charset) {
		BugLog.getInstance().assertNotNull(connection);
		BugLog.getInstance().assertNotNull(streamLogger);

		this.connection = connection;
		this.streamLogger = streamLogger;
		this.myCharset = charset;

		setInputStream(connection.getInputStream());
		setOutputStream(connection.getOutputStream());
	}

	public Reader createReader(InputStream inputStream) {
		return new InputStreamReader(inputStream);
	}

	public Writer createWriter(OutputStream outputStream) {
		try {
			return new OutputStreamWriter(outputStream, myCharset);
		}
		catch (UnsupportedEncodingException e) {
			return null;
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

  public void flushForReading() throws IOException {
    loggedWriter.flush();
    if (deflaterOutputStream != null) {
      deflaterOutputStream.finish();
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

	// Actions ================================================================

	public void setGzipped() throws IOException {
		loggedWriter.flush();
		loggedOutputStream.flush();

		deflaterOutputStream = new DeflaterOutputStream(connection.getOutputStream(), new Deflater(6));
		setOutputStream(deflaterOutputStream);

		setInputStream(new InflaterInputStream(connection.getInputStream()));
	}

	// Utils ==================================================================

	private void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;

		this.loggedInputStream = streamLogger.createLoggingInputStream(inputStream);
		this.loggedReader = createReader(this.loggedInputStream);
	}

	private void setOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;

		this.loggedOutputStream = streamLogger.createLoggingOutputStream(outputStream);
		this.loggedWriter = createWriter(this.loggedOutputStream);
	}
}
