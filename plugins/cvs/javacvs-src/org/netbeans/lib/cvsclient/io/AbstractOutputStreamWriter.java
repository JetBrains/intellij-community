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
import java.io.OutputStream;
import java.io.Writer;

/**
 * @author  Thomas Singer
 */
public abstract class AbstractOutputStreamWriter extends Writer {

	// Abstract ===============================================================

	protected abstract void writeChar(char chr, OutputStream outputStream) throws IOException;

	// Fields =================================================================

	private final OutputStream outputStream;

	// Setup ==================================================================

	protected AbstractOutputStreamWriter(OutputStream outputStream) {
		BugLog.getInstance().assertNotNull(outputStream);

		this.outputStream = outputStream;
	}

	// Implemented ============================================================

	public final void write(int chr) throws IOException {
		ensureOpen();

		writeChar((char)chr, outputStream);
	}

	public final void write(char[] buffer, int offset, int length) throws IOException {
		if (offset < 0
		        || length < 0
		        || offset + length > buffer.length) {
			throw new IndexOutOfBoundsException();
		}

		ensureOpen();

		for (; length > 0; offset++, length--) {
			final char chr = buffer[offset];
			writeChar(chr, outputStream);
		}
	}

	public final void flush() throws IOException {
		ensureOpen();
		outputStream.flush();
	}

	public final void close() throws IOException {
		ensureOpen();
		flush();
		outputStream.close();
	}

	// Utils ==================================================================

        private void ensureOpen() throws IOException {
		if (outputStream == null) {
			throw new IOException("Stream closed");
		}
	}
}
