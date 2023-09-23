/* ContentReader.java                                              NanoXML/Java
 *
 * $Revision: 1.4 $
 * $Date: 2002/01/04 21:03:28 $
 * $Name: RELEASE_2_2_1 $
 *
 * This file is part of NanoXML 2 for Java.
 * Copyright (C) 2000-2002 Marc De Scheemaecker, All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the
 * use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software in
 *     a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source distribution.
 */

package net.n3.nanoxml;


import java.io.IOException;
import java.io.Reader;


/**
 * This reader reads data from another reader until a new element has
 * been encountered.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 */
final class ContentReader extends Reader {

  /**
   * The encapsulated reader.
   */
  private final StdXMLReader reader;


  /**
   * Buffer.
   */
  private final String buffer;
  /**
   * The entity resolver.
   */
  private final IXMLEntityResolver resolver;
  /**
   * Pointer into the buffer.
   */
  private int bufferIndex;


  /**
   * Creates the reader.
   *
   * @param reader   the encapsulated reader
   * @param resolver the entity resolver
   * @param buffer   data that has already been read from <code>reader</code>
   */
  ContentReader(StdXMLReader reader, IXMLEntityResolver resolver, String buffer) {
    this.reader = reader;
    this.resolver = resolver;
    this.buffer = buffer;
    bufferIndex = 0;
  }


  /**
   * Reads a block of data.
   *
   * @param outputBuffer where to put the read data
   * @param offset       first position in buffer to put the data
   * @param size         maximum number of chars to read
   * @return the number of chars read, or -1 if at EOF
   * @throws IOException if an error occurred reading the data
   */
  @Override
  public int read(char[] outputBuffer, int offset, int size) throws IOException {
    try {
      int charsRead = 0;
      int bufferLength = buffer.length();

      if ((offset + size) > outputBuffer.length) {
        size = outputBuffer.length - offset;
      }

      while (charsRead < size) {
        String str;
        char ch;

        if (bufferIndex >= bufferLength) {
          str = XMLUtil.read(reader, '&');
          ch = str.charAt(0);
        }
        else {
          ch = buffer.charAt(bufferIndex);
          bufferIndex++;
          outputBuffer[charsRead] = ch;
          charsRead++;
          continue; // don't interpret chars in the buffer
        }

        if (ch == '<') {
          reader.unread(ch);
          break;
        }

        if ((ch == '&') && (str.length() > 1)) {
          if (str.charAt(1) == '#') {
            ch = XMLUtil.processCharLiteral(str);
          }
          else {
            XMLUtil.processEntity(str, reader, resolver);
            continue;
          }
        }

        outputBuffer[charsRead] = ch;
        charsRead++;
      }

      if (charsRead == 0) {
        charsRead = -1;
      }

      return charsRead;
    }
    catch (XMLParseException e) {
      throw new IOException(e.getMessage());
    }
  }


  /**
   * Skips the remaining data and closes the stream.
   *
   * @throws IOException if an error occurred reading the data
   */
  @Override
  public void close() throws IOException {
    try {
      int bufferLength = buffer.length();

      for (; ; ) {
        String str;
        char ch;

        if (bufferIndex >= bufferLength) {
          str = XMLUtil.read(reader, '&');
          ch = str.charAt(0);
        }
        else {
          bufferIndex++;
          continue; // don't interpret chars in the buffer
        }

        if (ch == '<') {
          reader.unread(ch);
          break;
        }

        if ((ch == '&') && (str.length() > 1)) {
          if (str.charAt(1) != '#') {
            XMLUtil.processEntity(str, reader, resolver);
          }
        }
      }
    }
    catch (XMLParseException e) {
      throw new IOException(e.getMessage());
    }
  }
}
