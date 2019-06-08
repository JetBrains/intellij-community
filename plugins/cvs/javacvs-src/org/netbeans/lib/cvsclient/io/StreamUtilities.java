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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Thomas Singer
 */
public final class StreamUtilities {

  private final String myCharset;

  public StreamUtilities(String charset) {
    this.myCharset = charset;
  }

  public String readLine(InputStream reader) throws IOException {
    if (myCharset != null) {
      return new String(readLineBytes(reader), myCharset);
    } else {
      return new String(readLineBytes(reader), StandardCharsets.UTF_8);
    }
  }

  public static byte[] readLineBytes(InputStream reader) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (; ;) {
      int value = reader.read();
      if ((char) value == '\n' || value == -1) {
        break;
      }

      output.write(value);
    }

    output.close();
    return output.toByteArray();
  }

}
