/*--

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.input.sax;

import org.jdom.Verifier;

import java.util.Arrays;

/**
 * A non-public utility class similar to StringBuilder but optimized for XML
 * parsing where the common case is that you get only one chunk of characters
 * per text section. TextBuffer stores the first chunk of characters in a
 * String, which can just be returned directly if no second chunk is received.
 * Subsequent chunks are stored in a supplemental char array (like StringBuilder
 * uses). In this case, the returned text will be the first String chunk,
 * concatenated with the subsequent chunks stored in the char array. This
 * provides optimal performance in the common case, while still providing very
 * good performance in the uncommon case. Furthermore, avoiding StringBuilder
 * means that no extra unused char array space will be kept around after parsing
 * is through.
 *
 * @author Bradley S. Huffman
 * @author Alex Rosen
 */
final class TextBuffer {

  /**
   * The text value. Only the first <code>arraySize</code> characters are
   * valid.
   */
  private char[] array = new char[1024];

  /**
   * The size of the text value.
   */
  private int arraySize = 0;

  /**
   * Constructor
   */
  TextBuffer() {
  }

  /**
   * Append the specified text to the text value of this buffer.
   *
   * @param source The char[] data to add
   * @param start  The offset in the data to start adding from
   * @param count  The number of chars to add.
   */
  void append(final char[] source, final int start, final int count) {
    if ((count + arraySize) > array.length) {
      // Fixes #112
      array = Arrays.copyOf(array, count + arraySize + (array.length >> 2));
    }
    System.arraycopy(source, start, array, arraySize, count);
    arraySize += count;
  }

  /**
   * Clears the text value and prepares the TextBuffer for reuse.
   */
  void clear() {
    arraySize = 0;
  }

  /**
   * Inspects the character data for non-whitespace
   *
   * @return true if all chars are whitespace
   */
  boolean isAllWhitespace() {
    int i = arraySize;
    while (--i >= 0) {
      if (!Verifier.isXMLWhitespace(array[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the text value stored in the buffer.
   */
  @Override
  public String toString() {
    if (arraySize == 0) {
      return "";
    }
    return String.valueOf(array, 0, arraySize);
  }
}
