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

package org.jdom;

import java.util.Iterator;
import java.util.List;

/**
 * A utility class to handle well-formedness checks on names, data, and other
 * verification tasks for JDOM. The class is final and may not be subclassed.
 *
 * @author Brett McLaughlin
 * @author Elliotte Rusty Harold
 * @author Jason Hunter
 * @author Bradley S. Huffman
 * @author Rolf Lear
 * @author Wilfried Middleton
 */
public final class Verifier {

  /*
   * KEY TO UNDERSTANDING MASKS.
   * ===========================
   *
   * This Verifier uses bitwise logic to perform fast validation on
   * XML characters. The concept is as follows...
   *
   * There are 7 major tests for characters in JDOM and one special case.
   * Can the character be a regular character, can it be part of an XML Name
   * (element, attribute, entity-ref, etc.), does it represent a letter,
   * digit, or combining character. Finally, can a character be the first
   * character in a name, or can the character be part of a URI. The special
   * case is that Attributes and Element names in JDOM do not include the
   * namespace prefix, thus, for Attribute and Elements, the name is the
   * identical test to other XML names, but excludes the ':'. For performance
   * reasons we only have the bitmask for the JDOM names, and then add the
   * ':' for the general case tests.
   *
   * These 7 tests are often performed in very tight performance critical
   * loops. It is essential for them to be fast.
   *
   * These 7 tests conveniently can be represented as 8 bits in a byte.
   * We can thus have a single byte that represents the possible roles for
   * each possible character. There are 64K characters... thus we need 64K
   * bytes to represent each character's possible roles.
   *
   * We could use arrays of booleans to accomplish the same thing, but each
   * boolean is a byte of memory, and using a bitmask allows us to put the
   * 8 bitmask tests in the same memory space as just one boolean array.
   *
   * The end solution is to have an array of these bytes, one per character,
   * and to then query each bit on the byte to see whether the corresponding
   * character is able to perform in the respective role.
   *
   * The complicated part of this process is three-fold. The hardest part is
   * knowing what role each character can play. The next hard part is
   * converting this knowledge in to an array of bytes we can express in this
   * Verifier class. The final part is querying that array for each test.
   *
   * Before this particular performance upgrade, the knowledge of what roles
   * each character can play was embedded in each of the isXML*() methods.
   * Those methods have been transferred in to the 'contrib' class
   * org.jdom.contrib.verifier.VerifierBuilder. That VerifierBuilder class
   * has a main method which takes that knowledge, and converts it in to a
   * 'compressed' set of two arrays, the byte mask, and the number of
   * consecutive characters that have that mask, which are then copy/pasted
   * in to this file as the VALCONST and LENCONST arrays.
   *
   * These two arrays are then 'decompressed' in to the CHARFLAGS array.
   *
   * The CHARFLAGS array is then queried for each of the 8 critical tests
   * to determine which roles a character performs.
   *
   * If you need to change the roles a character plays in XML (i.e. change
   * the return-value of one of the isXML...() methods, then you need to:
   *
   *  - update the logic in org.jdom.contrib.verifier.VerifierBuilder
   *  - run the VerifierBuilder
   *  - copy/paste the output to this file.
   *  - update the JUnit test harness TestVerifier
   */

  /**
   * The seed array used with LENCONST to populate CHARFLAGS.
   */
  private static final byte[] VALCONST = new byte[]{
    0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x41, 0x01,
    0x41, 0x49, 0x41, 0x59, 0x41, 0x01, 0x41, 0x01,
    0x41, 0x4f, 0x01, 0x4d, 0x01, 0x4f, 0x01, 0x41,
    0x01, 0x09, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x09, 0x01, 0x29, 0x01, 0x29,
    0x01, 0x0f, 0x09, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x29,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29,
    0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x09, 0x0f, 0x29,
    0x01, 0x19, 0x01, 0x29, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x29, 0x0f, 0x29,
    0x01, 0x29, 0x01, 0x19, 0x01, 0x29, 0x01, 0x0f,
    0x01, 0x29, 0x0f, 0x29, 0x01, 0x29, 0x01, 0x0f,
    0x29, 0x01, 0x19, 0x01, 0x29, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x29, 0x01, 0x0f, 0x01, 0x0f, 0x29,
    0x01, 0x19, 0x0f, 0x01, 0x29, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x29, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x19, 0x29, 0x0f, 0x01, 0x29, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x29, 0x0f, 0x29, 0x01,
    0x29, 0x01, 0x29, 0x01, 0x0f, 0x01, 0x19, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x29, 0x0f,
    0x29, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x19, 0x01, 0x29, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x29, 0x01, 0x19, 0x01, 0x29, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x19, 0x01, 0x29, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x19, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x19, 0x01, 0x0f, 0x01,
    0x0f, 0x29, 0x0f, 0x29, 0x01, 0x0f, 0x09, 0x29,
    0x01, 0x19, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x0f, 0x29, 0x0f, 0x29, 0x01,
    0x29, 0x0f, 0x01, 0x0f, 0x01, 0x09, 0x01, 0x29,
    0x01, 0x19, 0x01, 0x29, 0x01, 0x19, 0x01, 0x29,
    0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x0f, 0x01,
    0x0f, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x29, 0x01, 0x29, 0x01, 0x29, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x29, 0x01,
    0x29, 0x01, 0x0f, 0x01, 0x0f, 0x01, 0x0f, 0x01,
    0x0f, 0x01, 0x09, 0x01, 0x0f, 0x01, 0x0f, 0x29,
    0x01, 0x09, 0x01, 0x0f, 0x01, 0x29, 0x01, 0x09,
    0x01, 0x0f, 0x01, 0x09, 0x01, 0x0f, 0x01, 0x0f,
    0x01, 0x0f, 0x01, 0x00, 0x01, 0x00};

  /**
   * The seed array used with VALCONST to populate CHARFLAGS.
   */
  private static final int[] LENCONST = new int[]{
    9, 2, 2, 1, 18, 1, 1, 2,
    9, 2, 1, 10, 1, 2, 1, 1,
    2, 26, 4, 1, 1, 26, 3, 1,
    56, 1, 8, 23, 1, 31, 1, 58,
    2, 11, 2, 8, 1, 53, 1, 68,
    9, 36, 3, 2, 4, 30, 56, 89,
    18, 7, 14, 2, 46, 70, 26, 2,
    36, 1, 1, 3, 1, 1, 1, 20,
    1, 44, 1, 7, 3, 1, 1, 1,
    1, 1, 1, 1, 1, 18, 13, 12,
    1, 66, 1, 12, 1, 36, 1, 4,
    9, 53, 2, 2, 2, 2, 3, 28,
    2, 8, 2, 2, 55, 38, 2, 1,
    7, 38, 10, 17, 1, 23, 1, 3,
    1, 1, 1, 2, 1, 1, 11, 27,
    5, 3, 46, 26, 5, 1, 10, 8,
    13, 10, 6, 1, 71, 2, 5, 1,
    15, 1, 4, 1, 1, 15, 2, 2,
    1, 4, 2, 10, 519, 3, 1, 53,
    2, 1, 1, 16, 3, 4, 3, 10,
    2, 2, 10, 17, 3, 1, 8, 2,
    2, 2, 22, 1, 7, 1, 1, 3,
    4, 2, 1, 1, 7, 2, 2, 2,
    3, 9, 1, 4, 2, 1, 3, 2,
    2, 10, 2, 16, 1, 2, 6, 4,
    2, 2, 22, 1, 7, 1, 2, 1,
    2, 1, 2, 2, 1, 1, 5, 4,
    2, 2, 3, 11, 4, 1, 1, 7,
    10, 2, 3, 12, 3, 1, 7, 1,
    1, 1, 3, 1, 22, 1, 7, 1,
    2, 1, 5, 2, 1, 1, 8, 1,
    3, 1, 3, 18, 1, 5, 10, 17,
    3, 1, 8, 2, 2, 2, 22, 1,
    7, 1, 2, 2, 4, 2, 1, 1,
    6, 3, 2, 2, 3, 8, 2, 4,
    2, 1, 3, 4, 10, 18, 2, 1,
    6, 3, 3, 1, 4, 3, 2, 1,
    1, 1, 2, 3, 2, 3, 3, 3,
    8, 1, 3, 4, 5, 3, 3, 1,
    4, 9, 1, 15, 9, 17, 3, 1,
    8, 1, 3, 1, 23, 1, 10, 1,
    5, 4, 7, 1, 3, 1, 4, 7,
    2, 9, 2, 4, 10, 18, 2, 1,
    8, 1, 3, 1, 23, 1, 10, 1,
    5, 4, 7, 1, 3, 1, 4, 7,
    2, 7, 1, 1, 2, 4, 10, 18,
    2, 1, 8, 1, 3, 1, 23, 1,
    16, 4, 6, 2, 3, 1, 4, 9,
    1, 8, 2, 4, 10, 145, 46, 1,
    1, 1, 2, 7, 5, 6, 1, 8,
    1, 10, 39, 2, 1, 1, 2, 2,
    1, 1, 2, 1, 6, 4, 1, 7,
    1, 3, 1, 1, 1, 1, 2, 2,
    1, 2, 1, 1, 1, 2, 6, 1,
    2, 1, 2, 5, 1, 1, 1, 6,
    2, 10, 62, 2, 6, 10, 11, 1,
    1, 1, 1, 1, 4, 2, 8, 1,
    33, 7, 20, 1, 6, 4, 6, 1,
    1, 1, 21, 3, 7, 1, 1, 230,
    38, 10, 39, 9, 1, 1, 2, 1,
    3, 1, 1, 1, 2, 1, 5, 41,
    1, 1, 1, 1, 1, 11, 1, 1,
    1, 1, 1, 3, 2, 3, 1, 5,
    3, 1, 1, 1, 1, 1, 1, 1,
    1, 3, 2, 3, 2, 1, 1, 40,
    1, 9, 1, 2, 1, 2, 2, 7,
    2, 1, 1, 1, 7, 40, 1, 4,
    1, 8, 1, 3078, 156, 4, 90, 6,
    22, 2, 6, 2, 38, 2, 6, 2,
    8, 1, 1, 1, 1, 1, 1, 1,
    31, 2, 53, 1, 7, 1, 1, 3,
    3, 1, 7, 3, 4, 2, 6, 4,
    13, 5, 3, 1, 7, 211, 13, 4,
    1, 68, 1, 3, 2, 2, 1, 81,
    3, 3714, 1, 1, 1, 25, 9, 6,
    1, 5, 11, 84, 4, 2, 2, 2,
    2, 90, 1, 3, 6, 40, 7379, 20902,
    3162, 11172, 92, 2048, 8190, 2};

  /**
   * The number of characters in Java.
   */
  private static final int CHARCNT = Character.MAX_VALUE + 1;

  /**
   * An array of byte where each byte represents the roles that the
   * corresponding character can play. Use the bit mask values
   * to access each character's role.
   */
  private static final byte[] CHARFLAGS = buildBitFlags();

  /**
   * Convert the two compressed arrays in to th CHARFLAGS array.
   *
   * @return the CHARFLAGS array.
   */
  private static byte[] buildBitFlags() {
    final byte[] ret = new byte[CHARCNT];
    int index = 0;
    for (int i = 0; i < VALCONST.length; i++) {
      // v represents the roles a character can play.
      final byte v = VALCONST[i];
      // l is the number of consecutive chars that have the same
      // roles 'v'
      int l = LENCONST[i];
      // we need to give the next 'l' chars the role bits 'v'
      while (--l >= 0) {
        ret[index++] = v;
      }
    }
    return ret;
  }

  /**
   * Mask used to test for {@link #isXMLCharacter(int)}
   */
  private static final byte MASKXMLCHARACTER = 1;
  /**
   * Mask used to test for {@link #isXMLNameStartCharacter(char)}
   */
  private static final byte MASKXMLSTARTCHAR = 1 << 2;
  /**
   * Mask used to test for {@link #isXMLNameCharacter(char)}
   */
  private static final byte MASKXMLNAMECHAR = 1 << 3;

  /**
   * Ensure instantation cannot occur.
   */
  private Verifier() { }

  private static String checkJDOMName(final String name) {
    // Check basic XML name rules first
    // Cannot be empty or null
    if (name == null) {
      return "XML names cannot be null";
    }

    //final int len = name.length();
    if (name.isEmpty()) {
      return "XML names cannot be empty";
    }

    // Cannot start with a number
    if ((byte)0 == (CHARFLAGS[name.charAt(0)] & MASKXMLSTARTCHAR)) {
      return "XML name '" + name + "' cannot begin with the character \"" +
             name.charAt(0) + "\"";
    }
    // Ensure legal content for non-first chars
    // also check char 0 to catch colon char ':'
    for (int i = name.length() - 1; i >= 1; i--) {
      if ((byte)0 == (byte)(CHARFLAGS[name.charAt(i)] & MASKXMLNAMECHAR)) {
        return "XML name '" + name + "' cannot contain the character \""
               + name.charAt(i) + "\"";
      }
    }

    // If we got here, everything is OK
    return null;
  }

  /**
   * This will check the supplied name to see if it is legal for use as
   * a JDOM <code>{@link Element}</code> name.
   *
   * @param name <code>String</code> name to check.
   * @return <code>String</code> reason name is illegal, or
   * <code>null</code> if name is OK.
   */
  public static String checkElementName(final String name) {
    return checkJDOMName(name);
  }

  /**
   * This will check the supplied name to see if it is legal for use as
   * a JDOM <code>{@link Attribute}</code> name.
   *
   * @param name <code>String</code> name to check.
   * @return <code>String</code> reason name is illegal, or
   * <code>null</code> if name is OK.
   */
  static String checkAttributeName(final String name) {
    // Attribute names may not be xmlns since we do this internally too
    if ("xmlns".equals(name)) {
      return "An Attribute name may not be \"xmlns\"; " +
             "use the Namespace class to manage namespaces";
    }

    return checkJDOMName(name);
  }

  /**
   * This will check the supplied string to see if it only contains
   * characters allowed by the XML 1.0 specification. The C0 controls
   * (e.g. null, vertical tab, form-feed, etc.) are specifically excluded
   * except for carriage return, line-feed, and the horizontal tab.
   * Surrogates are also excluded.
   * <p>
   * This method is useful for checking element content and attribute
   * values. Note that characters
   * like " and &lt; are allowed in attribute values and element content.
   * They will simply be escaped as &quot; or &lt;
   * when the value is serialized.
   * </p>
   *
   * @param text <code>String</code> value to check.
   * @return <code>String</code> reason name is illegal, or
   * <code>null</code> if name is OK.
   */
  public static String checkCharacterData(final String text) {
    if (text == null) {
      return "A null is not a legal XML value";
    }

    final int len = text.length();
    for (int i = 0; i < len; i++) {
      // we are expecting a normal char, but may be a surrogate.
      // the isXMLCharacter method takes an int argument, but we have a char.
      // we save a lot of time by doing the test directly here without
      // doing the unnecessary cast-to-int and double-checking ranges
      // for the char.
      // Also, note that we only need to check for non-zero flags, instead
      // of checking for an actual bit, because all the other
      // character roles are a pure subset of CharacterData. Put another way,
      // any character with any bit set, will always also have the
      // CharacterData bit set.
      while (CHARFLAGS[text.charAt(i)] != (byte)0) {
        // fast-loop through the chars until we find something that's not.
        //noinspection AssignmentToForLoopParameter
        if (++i == len) {
          // we passed all the characters...
          return null;
        }
      }
      // the character is not a normal character.
      // we need to sort out what it is. Neither high nor low
      // surrogate pairs are valid characters, so they will get here.

      if (isHighSurrogate(text.charAt(i))) {
        // we have the valid high char of a pair.
        // we will expect the low char on the next index,
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i >= len) {
          return String.format("Truncated Surrogate Pair 0x%04x????",
                               (int)text.charAt(i - 1));
        }
        if (isLowSurrogate(text.charAt(i))) {
          // we now have the low char of a pair, decode and validate
          if (!isXMLCharacter(decodeSurrogatePair(
            text.charAt(i - 1), text.charAt(i)))) {
            // Likely this character can't be easily displayed
            // because it's a control, so we use it'd hexadecimal
            // representation in the reason.
            return String.format("0x%06x is not a legal XML character",
                                 decodeSurrogatePair(
                                   text.charAt(i - 1), text.charAt(i)));
          }
        }
        else {
          // we got a normal character, but we wanted a low surrogate
          return String.format("Illegal Surrogate Pair 0x%04x%04x",
                               (int)text.charAt(i - 1), (int)text.charAt(i));
        }
      }
      else {
        // Likely this character can't be easily displayed
        // because it's a control, so we use its hexadecimal
        // representation in the reason.
        return String.format("0x%04x is not a legal XML character",
                             (int)text.charAt(i));
      }
    }

    // If we got here, everything is OK
    return null;
  }

  /**
   * This will check the supplied data to see if it is legal for use as
   * JDOM <code>{@link CDATA}</code>.
   *
   * @param data <code>String</code> data to check.
   * @return <code>String</code> reason data is illegal, or
   * <code>null</code> is name is OK.
   */
  static String checkCDATASection(final String data) {
    String reason;
    if ((reason = checkCharacterData(data)) != null) {
      return reason;
    }

    if (data.contains("]]>")) {
      return "CDATA cannot internally contain a CDATA ending " +
             "delimiter (]]>)";
    }

    // If we got here, everything is OK
    return null;
  }

  /**
   * This will check the supplied name to see if it is legal for use as
   * a JDOM <code>{@link Namespace}</code> prefix.
   *
   * @param prefix <code>String</code> prefix to check.
   * @return <code>String</code> reason name is illegal, or
   * <code>null</code> if name is OK.
   */
  public static String checkNamespacePrefix(final String prefix) {
    // Manually do rule, since URIs can be null or empty
    if ((prefix == null) || (prefix.isEmpty())) {
      return null;
    }

    if (checkJDOMName(prefix) != null) {
      // will double-check null and empty names, but that's OK
      // since we have already checked them.
      return checkJDOMName(prefix);
    }

    // Cannot start with "xml" in any character case
		/* See Issue 126 - https://github.com/hunterhacker/jdom/issues/126
		if (prefix.length() >= 3) {
			if (prefix.charAt(0) == 'x' || prefix.charAt(0) == 'X') {
				if (prefix.charAt(1) == 'm' || prefix.charAt(1) == 'M') {
					if (prefix.charAt(2) == 'l' || prefix.charAt(2) == 'L') {
						return "Namespace prefixes cannot begin with " +
								"\"xml\" in any combination of case";
					}
				}
			}
		}
		*/

    // If we got here, everything is OK
    return null;
  }

  /**
   * This will check the supplied name to see if it is legal for use as
   * a JDOM <code>{@link Namespace}</code> URI.
   * <p>
   * This is a 'light' test of URI's designed to filter out only the worst
   * illegal URIs. It tests only to ensure the first character is valid. A
   * comprehensive URI validation process would be impractical.
   *
   * @param uri <code>String</code> URI to check.
   * @return <code>String</code> reason name is illegal, or
   * <code>null</code> if name is OK.
   */
  static String checkNamespaceURI(final String uri) {
    // Manually do rule, since URIs can be null or empty
    if ((uri == null) || (uri.isEmpty())) {
      return null;
    }

    // Cannot start with a number
    final char first = uri.charAt(0);
    if (Character.isDigit(first)) {
      return "Namespace URIs cannot begin with a number";
    }
    // Cannot start with a $
    if (first == '$') {
      return "Namespace URIs cannot begin with a dollar sign ($)";
    }
    // Cannot start with a -
    if (first == '-') {
      return "Namespace URIs cannot begin with a hyphen (-)";
    }

    // Cannot start with space...
    if (isXMLWhitespace(first)) {
      return "Namespace URIs cannot begin with white-space";
    }

    // If we got here, everything is OK
    return null;
  }

  /**
   * Check if two namespaces collide.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param other     <code>Namespace</code> to check against.
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  private static String checkNamespaceCollision(final Namespace namespace,
                                                final Namespace other) {
    String p1, p2, u1, u2, reason;

    reason = null;
    p1 = namespace.getPrefix();
    u1 = namespace.getURI();
    p2 = other.getPrefix();
    u2 = other.getURI();
    if (p1.equals(p2) && !u1.equals(u2)) {
      reason = "The namespace prefix \"" + p1 + "\" collides";
    }
    return reason;
  }

  /**
   * Check if <code>{@link Attribute}</code>'s namespace collides with a
   * <code>{@link Element}</code>'s namespace.
   *
   * @param attribute <code>Attribute</code> to check.
   * @param element   <code>Element</code> to check against.
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  static String checkNamespaceCollision(final Attribute attribute,
                                        final Element element) {
    return checkNamespaceCollision(attribute, element, -1);
  }

  /**
   * Check if <code>{@link Attribute}</code>'s namespace collides with a
   * <code>{@link Element}</code>'s namespace.
   *
   * @param attribute <code>Attribute</code> to check.
   * @param element   <code>Element</code> to check against.
   * @param ignoreAtt Ignore a specific Attribute (if it exists) when
   *                  calculating any collisions (used when replacing one attribute
   *                  with another).
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  static String checkNamespaceCollision(final Attribute attribute,
                                        final Element element, final int ignoreAtt) {
    final Namespace namespace = attribute.getNamespace();
    final String prefix = namespace.getPrefix();
    if ("".equals(prefix)) {
      return null;
    }

    return checkNamespaceCollision(namespace, element, ignoreAtt);
  }

  /**
   * Check if a <code>{@link Namespace}</code> collides with a
   * <code>{@link Element}</code>'s namespace.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param element   <code>Element</code> to check against.
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  static String checkNamespaceCollision(final Namespace namespace,
                                        final Element element) {
    return checkNamespaceCollision(namespace, element, -1);
  }

  /**
   * Check if a <code>{@link Namespace}</code> collides with a
   * <code>{@link Element}</code>'s namespace.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param element   <code>Element</code> to check against.
   * @param ignoreAtt Ignore a specific Attribute (if it exists) when
   *                  calculating any collisions (used when replacing one attribute
   *                  with another).
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  private static String checkNamespaceCollision(final Namespace namespace,
                                                final Element element, final int ignoreAtt) {
    String reason = checkNamespaceCollision(namespace,
                                            element.getNamespace());
    if (reason != null) {
      return reason + " with the element namespace prefix";
    }

    if (element.hasAdditionalNamespaces()) {
      reason = checkNamespaceCollision(namespace,
                                       element.getAdditionalNamespaces());
      if (reason != null) {
        return reason;
      }
    }

    if (element.hasAttributes()) {
      reason = checkNamespaceCollision(namespace, element.getAttributes(), ignoreAtt);
      if (reason != null) {
        return reason;
      }
    }

    return null;
  }

  /**
   * Check if a <code>{@link Namespace}</code> collides with a
   * <code>{@link Attribute}</code>'s namespace.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param attribute <code>Attribute</code> to check against.
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  static String checkNamespaceCollision(final Namespace namespace, final Attribute attribute) {
    String reason = null;
    if (!attribute.getNamespace().equals(Namespace.NO_NAMESPACE)) {
      reason = checkNamespaceCollision(namespace,
                                       attribute.getNamespace());
      if (reason != null) {
        reason += " with an attribute namespace prefix on the element";
      }
    }
    return reason;
  }

  /**
   * Check if a <code>{@link Namespace}</code> collides with any namespace
   * from a list of objects.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param list      <code>List</code> to check against.
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  static String checkNamespaceCollision(Namespace namespace, List<?> list) {
    return checkNamespaceCollision(namespace, list, -1);
  }

  /**
   * Check if a <code>{@link Namespace}</code> collides with any namespace
   * from a list of objects.
   *
   * @param namespace <code>Namespace</code> to check.
   * @param list      <code>List</code> to check against.
   * @param ignoreAtt Ignore a specific Attribute (if it exists) when
   *                  calculating any collisions (used when replacing one attribute
   *                  with another).
   * @return <code>String</code> reason for collision, or
   * <code>null</code> if no collision.
   */
  private static String checkNamespaceCollision(Namespace namespace, List<?> list, final int ignoreAtt) {
    if (list == null) {
      return null;
    }

    String reason = null;
    final Iterator<?> i = list.iterator();
    int cnt = -1;
    while ((reason == null) && i.hasNext()) {
      final Object obj = i.next();
      cnt++;
      if (obj instanceof Attribute) {
        if (cnt == ignoreAtt) {
          continue;
        }
        reason = checkNamespaceCollision(namespace, (Attribute)obj);
      }
      else if (obj instanceof Element) {
        reason = checkNamespaceCollision(namespace, (Element)obj);
      }
      else if (obj instanceof Namespace) {
        reason = checkNamespaceCollision(namespace, (Namespace)obj);
        if (reason != null) {
          reason += " with an additional namespace declared" +
                    " by the element";
        }
      }
    }
    return reason;
  }


  /**
   * This will check the supplied data to see if it is legal for use as
   * JDOM <code>{@link Comment}</code> data.
   *
   * @param data <code>String</code> data to check.
   * @return <code>String</code> reason data is illegal, or
   * <code>null</code> if data is OK.
   */
  static String checkCommentData(final String data) {
    String reason;
    if ((reason = checkCharacterData(data)) != null) {
      return reason;
    }

    if (data.contains("--")) {
      return "Comments cannot contain double hyphens (--)";
    }
    if (data.endsWith("-")) {
      return "Comment data cannot end with a hyphen.";
    }

    // If we got here, everything is OK
    return null;
  }

  /**
   * This is a utility function to decode a non-BMP
   * UTF-16 surrogate pair.
   *
   * @param high high 16 bits
   * @param low  low 16 bits
   * @return decoded character
   */
  public static int decodeSurrogatePair(final char high, final char low) {
    return 0x10000 + (high - 0xD800) * 0x400 + (low - 0xDC00);
  }

  /**
   * This will check the supplied data to see if it is legal for use as
   * PublicID (in a {@link DocType} or {@link EntityRef}).
   *
   * @param c the character to validate
   * @return <code>String</code> reason <i>c</i> is illegal, or
   * <code>null</code> if <i>c</i> is OK.
   */
  private static boolean isXMLPublicIDCharacter(final char c) {
    // [13] PubidChar ::= #x20 | #xD | #xA | [a-zA-Z0-9] |
    // [-'()+,./:=?;*#@$_%]

    if (c >= 'a' && c <= 'z') return true;
    if (c >= '?' && c <= 'Z') return true;
    if (c >= '\'' && c <= ';') return true;

    if (c == ' ') return true;
    if (c == '!') return true;
    if (c == '=') return true;
    if (c == '#') return true;
    if (c == '$') return true;
    if (c == '_') return true;
    if (c == '%') return true;
    if (c == '\n') return true;
    if (c == '\r') return true;
    if (c == '\t') return true;

    return false;
  }

  /**
   * This will ensure that the data for a public identifier
   * is legal.
   *
   * @param publicID <code>String</code> public ID to check.
   * @return <code>String</code> reason public ID is illegal, or
   * <code>null</code> if public ID is OK.
   */
  static String checkPublicID(final String publicID) {
    String reason = null;

    if (publicID == null) return null;
    // This indicates there is no public ID

    for (int i = 0; i < publicID.length(); i++) {
      final char c = publicID.charAt(i);
      if (!isXMLPublicIDCharacter(c)) {
        reason = c + " is not a legal character in public IDs";
        break;
      }
    }

    return reason;
  }


  /**
   * This will ensure that the data for a system literal
   * is legal.
   *
   * @param systemLiteral <code>String</code> system literal to check.
   * @return <code>String</code> reason system literal is illegal, or
   * <code>null</code> if system literal is OK.
   */
  static String checkSystemLiteral(final String systemLiteral) {
    String reason;

    if (systemLiteral == null) return null;
    // This indicates there is no system ID

    if (systemLiteral.indexOf('\'') != -1
        && systemLiteral.indexOf('"') != -1) {
      reason =
        "System literals cannot simultaneously contain both single and double quotes.";
    }
    else {
      reason = checkCharacterData(systemLiteral);
    }

    return reason;
  }

  /**
   * This is a utility function for sharing the base process of checking
   * any XML name.
   *
   * @param name <code>String</code> to check for XML name compliance.
   * @return <code>String</code> reason the name is illegal, or
   * <code>null</code> if OK.
   */
  public static String checkXMLName(final String name) {
    // Cannot be empty or null
    if ((name == null)) {
      return "XML names cannot be null";
    }

    final int len = name.length();
    if (len == 0) {
      return "XML names cannot be empty";
    }


    // Cannot start with a number
    if (!isXMLNameStartCharacter(name.charAt(0))) {
      return "XML names cannot begin with the character \"" +
             name.charAt(0) + "\"";
    }
    // Ensure legal content for non-first chars
    for (int i = 1; i < len; i++) {
      if (!isXMLNameCharacter(name.charAt(i))) {
        return "XML names cannot contain the character \"" + name.charAt(i) + "\"";
      }
    }

    // We got here, so everything is OK
    return null;
  }

  /**
   * This is a function for determining whether the
   * specified character is the high 16 bits in a
   * UTF-16 surrogate pair.
   *
   * @param ch character to check
   * @return true if the character is a high surrogate, false otherwise
   */
  public static boolean isHighSurrogate(final char ch) {
    // faster way to do it is with bit manipulation....
    // return (ch >= 0xD800 && ch <= 0xDBFF);
    // A high surrogate has the bit pattern:
    //    110110xx xxxxxxxx
    // ch & 0xFC00 does a bit-mask of the most significant 6 bits (110110)
    // return 0xD800 == (ch & 0xFC00);
    // as it happens, it is faster to do a bit-shift,
    return 0x36 == ch >>> 10;
  }

  /**
   * This is a function for determining whether the
   * specified character is the low 16 bits in a
   * UTF-16 surrogate pair.
   *
   * @param ch character to check
   * @return true if the character is a low surrogate, false otherwise.
   */
  public static boolean isLowSurrogate(final char ch) {
    // faster way to do it is with bit manipulation....
    // return (ch >= 0xDC00 && ch <= 0xDFFF);
    return 0x37 == ch >>> 10;
  }

  /**
   * This is a utility function for determining whether a specified
   * character is a character according to production 2 of the
   * XML 1.0 specification.
   *
   * @param c <code>char</code> to check for XML compliance
   * @return <code>boolean</code> true if it's a character,
   * false otherwise
   */
  public static boolean isXMLCharacter(final int c) {
    if (c >= CHARCNT) {
      return c <= 0x10FFFF;
    }
    return (byte)0 != (byte)(CHARFLAGS[c] & MASKXMLCHARACTER);
  }


  /**
   * This is a utility function for determining whether a specified
   * character is a name character according to production 4 of the
   * XML 1.0 specification.
   *
   * @param c <code>char</code> to check for XML name compliance.
   * @return <code>boolean</code> true if it's a name character,
   * false otherwise.
   */
  public static boolean isXMLNameCharacter(final char c) {
    return (byte)0 != (byte)(CHARFLAGS[c] & MASKXMLNAMECHAR) || c == ':';
  }

  /**
   * This is a utility function for determining whether a specified
   * character is a legal name start character according to production 5
   * of the XML 1.0 specification. This production does allow names
   * to begin with colons which the Namespaces in XML Recommendation
   * disallows.
   *
   * @param c <code>char</code> to check for XML name start compliance.
   * @return <code>boolean</code> true if it's a name start character,
   * false otherwise.
   */
  public static boolean isXMLNameStartCharacter(final char c) {
    return (byte)0 != (byte)(CHARFLAGS[c] & MASKXMLSTARTCHAR) || c == ':';
  }

  /**
   * This is a utility function for determining whether a specified
   * Unicode character is a whitespace character according to production 3
   * of the XML 1.0 specification.
   *
   * @param c <code>char</code> to check for XML whitespace compliance
   * @return <code>boolean</code> true if it's a whitespace, false otherwise
   */
  public static boolean isXMLWhitespace(final char c) {
    // the following if is faster than switch statements.
    // seems the implicit conversion to int is slower than
    // the fall-through or's
    if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
      return true;
    }
    return false;
  }

  /**
   * This is a utility function for determining whether a specified
   * String is a whitespace character according to production 3
   * of the XML 1.0 specification.
   * <p>
   * This method delegates the individual calls for each character to
   * {@link #isXMLWhitespace(char)}.
   *
   * @param value The value to inspect
   * @return true if all characters in the input value are all whitespace
   * (or the string is the empty-string).
   * @since JDOM2
   */
  public static boolean isAllXMLWhitespace(final String value) {
    // Doing the count-down instead of a count-up saves a single int
    // variable declaration.
    int i = value.length();
    while (--i >= 0) {
      if (!isXMLWhitespace(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
