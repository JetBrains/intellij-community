package org.netbeans.lib.cvsclient.io;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author  Thomas Singer
 */
public interface IStreamLogger {

	OutputStream createLoggingOutputStream(OutputStream outputStream);

	InputStream createLoggingInputStream(InputStream inputStream);

	OutputStream getInputLogStream();

	OutputStream getOutputLogStream();
}
