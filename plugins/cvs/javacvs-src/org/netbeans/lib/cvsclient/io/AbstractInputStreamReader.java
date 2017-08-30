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
package org.netbeans.lib.cvsclient.io;

import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * @author  Thomas Singer
 */
public abstract class AbstractInputStreamReader extends Reader {

	// Abstract ===============================================================

	protected abstract int readChar(InputStream inputStream);

	// Fields =================================================================

	private InputStream inputStream;

	// Setup ==================================================================

	protected AbstractInputStreamReader(InputStream inputStream) {
		BugLog.getInstance().assertNotNull(inputStream);

		this.inputStream = inputStream;
	}

	// Implemented ============================================================

	public final int read() throws IOException {
		ensureOpen();

		return readChar(inputStream);
	}

	public final int read(char buffer[], int startOffset, int length) throws IOException {
		if (startOffset < 0
		        || startOffset > buffer.length
		        || length < 0
		        || startOffset + length > buffer.length
		        || startOffset + length < 0) {
			throw new IndexOutOfBoundsException();
		}

		ensureOpen();

		int charsRead = 0;
		for (int offset = startOffset; length > 0; offset++, charsRead++, length--) {
			final int value = readChar(inputStream);
			if (value < 0) {
				break;
			}

			buffer[offset] = (char)value;
		}

		return charsRead > 0 ? charsRead : -1;
	}

	public final boolean ready() throws IOException {
		ensureOpen();
		try {
			return inputStream.available() > 0;
		}
		catch (IOException ex) {
			return false;
		}
	}

	public final void close() throws IOException {
		if (inputStream == null) {
			return;
		}
		inputStream.close();
		inputStream = null;
	}

	// Utils ==================================================================

        private void ensureOpen() throws IOException {
		if (inputStream == null) {
			throw new IOException("Stream closed");
		}
	}
}
