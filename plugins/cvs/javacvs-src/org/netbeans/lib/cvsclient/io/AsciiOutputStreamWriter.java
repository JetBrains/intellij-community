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

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author  Thomas Singer
 */
public final class AsciiOutputStreamWriter extends AbstractOutputStreamWriter {

	// Setup ==================================================================

	public AsciiOutputStreamWriter(OutputStream outputStream) {
		super(outputStream);
	}

	// Implemented ============================================================

	@Override
        protected void writeChar(char chr, OutputStream outputStream) throws IOException {
          final int aChar = chr;//(chr & 0xFF00);
          /*
          if (aChar > 0) {
                  throw new EncodingException("Cannot convert character " + Integer.toHexString(chr));
          }

		final int value = chr & 0xFF;
          */
		outputStream.write(aChar);
	}
}
