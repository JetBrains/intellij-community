/* CDATAReader.java                                                NanoXML/Java
 *
 * $Revision: 1.3 $
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
 * This reader reads data from another reader until the end of a CDATA section
 * (]]&gt;) has been encountered.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
final class CDATAReader extends Reader {

  /**
   * The encapsulated reader.
   */
  private final StdXMLReader reader;


  /**
   * Saved char.
   */
  private char savedChar;


  /**
   * True if the end of the stream has been reached.
   */
  private boolean atEndOfData;


  /**
   * Creates the reader.
   *
   * @param reader the encapsulated reader
   */
  CDATAReader(StdXMLReader reader) {
    this.reader = reader;
    savedChar = 0;
    atEndOfData = false;
  }


  /**
   * Reads a block of data.
   *
   * @param buffer where to put the read data
   * @param offset first position in buffer to put the data
   * @param size   maximum number of chars to read
   * @return the number of chars read, or -1 if at EOF
   * @throws IOException if an error occurred reading the data
   */
  @Override
  public int read(char[] buffer, int offset, int size) throws IOException {
    int charsRead = 0;

    if (atEndOfData) {
      return -1;
    }

    if ((offset + size) > buffer.length) {
      size = buffer.length - offset;
    }

    while (charsRead < size) {
      char ch = savedChar;

      if (ch == 0) {
        ch = reader.read();
      }
      else {
        savedChar = 0;
      }

      if (ch == ']') {
        char ch2 = reader.read();

        if (ch2 == ']') {
          char ch3 = reader.read();

          if (ch3 == '>') {
            atEndOfData = true;
            break;
          }

          savedChar = ch2;
          reader.unread(ch3);
        }
        else {
          reader.unread(ch2);
        }
      }
      buffer[charsRead] = ch;
      charsRead++;
    }

    if (charsRead == 0) {
      charsRead = -1;
    }

    return charsRead;
  }


  /**
   * Skips remaining data and closes the stream.
   *
   * @throws IOException if an error occurred reading the data
   */
  @Override
  public void close() throws IOException {
    while (!atEndOfData) {
      char ch = savedChar;

      if (ch == 0) {
        ch = reader.read();
      }
      else {
        savedChar = 0;
      }

      if (ch == ']') {
        char ch2 = reader.read();

        if (ch2 == ']') {
          char ch3 = reader.read();

          if (ch3 == '>') {
            break;
          }

          savedChar = ch2;
          reader.unread(ch3);
        }
        else {
          reader.unread(ch2);
        }
      }
    }

    atEndOfData = true;
  }
}
