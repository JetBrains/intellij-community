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
import java.io.Reader;

/**
 * @author Thomas Singer
 */
public final class StreamUtilities {
  public static String readLine(Reader reader) throws IOException {
    return new String(readLineBytes(reader, true));
  }

  public static byte[] readLineBytes(Reader reader, boolean ignoreNegative) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (; ;) {
      int value = reader.read();

      if (ignoreNegative) {
        if (value < 0) {
          if (output.size() == 0) {
            continue;
          }

          break;
        }
      }

      if ((char)value == '\n') {
        break;
      }

      output.write(value);
    }

    output.close();
    return output.toByteArray();
  }

}
