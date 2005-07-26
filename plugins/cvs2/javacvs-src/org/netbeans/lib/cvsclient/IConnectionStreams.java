package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.file.IReaderFactory;
import org.netbeans.lib.cvsclient.file.IWriterFactory;

import java.io.*;

/**
 * @author  Thomas Singer
 */
public interface IConnectionStreams {
	InputStream getLoggedInputStream();

	OutputStream getLoggedOutputStream();

	Reader getLoggedReader();

	Writer getLoggedWriter();

	InputStream getInputStream();

	OutputStream getOutputStream();

  IReaderFactory getReaderFactory();

	IWriterFactory getWriterFactory();

	void flushForReading() throws IOException;

	void close();
}
