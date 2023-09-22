/* XMLUtil.java                                                    NanoXML/Java
 *
 * $Revision: 1.5 $
 * $Date: 2002/02/03 21:19:38 $
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
 * Utility methods for NanoXML.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.5 $
 */
final class XMLUtil {
  /**
   * Skips the remainder of a comment.
   * It is assumed that &lt;!- is already read.
   *
   * @param reader the reader
   * @throws IOException if an error occurred reading the data
   */
  static void skipComment(StdXMLReader reader) throws IOException, XMLParseException {
    if (reader.read() != '-') {
      errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "<!--");
    }

    int dashesRead = 0;

    for (; ; ) {
      char ch = reader.read();

      switch (ch) {
        case '-':
          dashesRead++;
          break;

        case '>':
          if (dashesRead == 2) {
            return;
          }

        default:
          dashesRead = 0;
      }
    }
  }


  /**
   * Skips the remainder of the current XML tag.
   *
   * @param reader the reader
   * @throws IOException if an error occurred reading the data
   */
  static void skipTag(StdXMLReader reader) throws IOException {
    int level = 1;

    while (level > 0) {
      char ch = reader.read();

      switch (ch) {
        case '<':
          ++level;
          break;

        case '>':
          --level;
          break;
      }
    }
  }


  /**
   * Scans a public ID.
   *
   * @param publicID will contain the public ID
   * @param reader   the reader
   * @return the system ID
   * @throws IOException if an error occurred reading the data
   */
  static String scanPublicID(StringBuilder publicID, StdXMLReader reader) throws IOException, XMLParseException {
    if (!checkLiteral(reader, "UBLIC")) {
      return null;
    }

    skipWhitespace(reader, null);
    publicID.append(scanString(reader, '\0', null));
    skipWhitespace(reader, null);
    return scanString(reader, '\0', null);
  }


  /**
   * Scans a system ID.
   *
   * @param reader the reader
   * @return the system ID
   * @throws IOException if an error occurred reading the data
   */
  static String scanSystemID(StdXMLReader reader) throws IOException, XMLParseException {
    if (!checkLiteral(reader, "YSTEM")) {
      return null;
    }

    skipWhitespace(reader, null);
    return scanString(reader, '\0', null);
  }


  /**
   * Retrieves an identifier from the data.
   *
   * @param reader the reader
   * @throws IOException if an error occurred reading the data
   */
  static String scanIdentifier(StdXMLReader reader) throws IOException {
    StringBuilder result = new StringBuilder();

    for (; ; ) {
      char ch = reader.read();

      if ((ch == '_') ||
          (ch == ':') ||
          (ch == '-') ||
          (ch == '.') ||
          ((ch >= 'a') && (ch <= 'z')) ||
          ((ch >= 'A') && (ch <= 'Z')) ||
          ((ch >= '0') && (ch <= '9')) ||
          (ch > '\u007E')) {
        result.append(ch);
      }
      else {
        reader.unread(ch);
        break;
      }
    }

    return result.toString();
  }


  /**
   * Retrieves a delimited string from the data.
   *
   * @param reader         the reader
   * @param entityChar     the escape character (&amp; or %)
   * @param entityResolver the entity resolver
   * @throws IOException if an error occurred reading the data
   */
  static String scanString(StdXMLReader reader, char entityChar, IXMLEntityResolver entityResolver) throws IOException, XMLParseException {
    StringBuilder result = new StringBuilder();
    int startingLevel = reader.getStreamLevel();
    char delim = reader.read();

    if ((delim != '\'') && (delim != '"')) {
      errorExpectedInput(reader.getSystemID(), reader.getLineNr(), "delimited string");
    }

    for (; ; ) {
      String str = read(reader, entityChar);
      char ch = str.charAt(0);

      if (ch == entityChar) {
        if (str.charAt(1) == '#') {
          result.append(processCharLiteral(str));
        }
        else {
          processEntity(str, reader, entityResolver);
        }
      }
      else if (ch == '&') {
        reader.unread(ch);
        str = read(reader, '&');
        if (str.charAt(1) == '#') {
          result.append(processCharLiteral(str));
        }
        else {
          result.append(str);
        }
      }
      else if (reader.getStreamLevel() == startingLevel) {
        if (ch == delim) {
          break;
        }
        else if ((ch == 9) || (ch == 10) || (ch == 13)) {
          result.append(' ');
        }
        else {
          result.append(ch);
        }
      }
      else {
        result.append(ch);
      }
    }

    return result.toString();
  }


  /**
   * Processes an entity.
   *
   * @param entity         the entity
   * @param reader         the reader
   * @param entityResolver the entity resolver
   */
  static void processEntity(String entity, StdXMLReader reader, IXMLEntityResolver entityResolver) throws XMLParseException {
    entity = entity.substring(1, entity.length() - 1);
    Reader entityReader = entityResolver.getEntity(reader, entity);

    if (entityReader == null) {
      errorInvalidEntity(reader.getSystemID(), reader.getLineNr(), entity);
    }

    boolean externalEntity = entityResolver.isExternalEntity(entity);
    reader.startNewStream(entityReader, !externalEntity);
  }


  /**
   * Processes a character literal.
   *
   * @param entity the entity
   */
  static char processCharLiteral(String entity) {
    if (entity.charAt(2) == 'x') {
      entity = entity.substring(3, entity.length() - 1);
      return (char)Integer.parseInt(entity, 16);
    }
    else {
      entity = entity.substring(2, entity.length() - 1);
      return (char)Integer.parseInt(entity, 10);
    }
  }


  /**
   * Skips whitespace from the reader.
   *
   * @param reader the reader
   * @param buffer where to put the whitespace; null if the
   *               whitespace does not have to be stored.
   * @throws IOException if an error occurred reading the data
   */
  static void skipWhitespace(StdXMLReader reader, StringBuilder buffer) throws IOException {
    char ch;

    if (buffer == null) {
      do {
        ch = reader.read();
      }
      while ((ch == ' ') || (ch == '\t') || (ch == '\n'));
    }
    else {
      for (; ; ) {
        ch = reader.read();

        if ((ch != ' ') && (ch != '\t') && (ch != '\n')) {
          break;
        }

        if (ch == '\n') {
          buffer.append('\n');
        }
        else {
          buffer.append(' ');
        }
      }
    }

    reader.unread(ch);
  }


  /**
   * Reads a character from the reader.
   *
   * @param reader     the reader
   * @param entityChar the escape character (&amp; or %) used to indicate
   *                   an entity
   * @return the character, or an entity expression (like e.g. &amp;lt;)
   * @throws IOException if an error occurred reading the data
   */
  static String read(StdXMLReader reader, char entityChar) throws IOException {
    char ch = reader.read();
    StringBuilder buf = new StringBuilder();
    buf.append(ch);

    if (ch == entityChar) {
      while (ch != ';') {
        ch = reader.read();
        buf.append(ch);
      }
    }

    return buf.toString();
  }


  /**
   * Reads a character from the reader disallowing entities.
   *
   * @param reader     the reader
   * @param entityChar the escape character (&amp; or %) used to indicate
   *                   an entity
   */
  static char readChar(StdXMLReader reader, char entityChar) throws IOException, XMLParseException {
    String str = read(reader, entityChar);
    char ch = str.charAt(0);

    if (ch == entityChar) {
      errorUnexpectedEntity(reader.getSystemID(), reader.getLineNr(), str);
    }

    return ch;
  }


  /**
   * Returns true if the data starts with <I>literal</I>.
   * Enough chars are read to determine this result.
   *
   * @param reader  the reader
   * @param literal the literal to check
   * @throws IOException if an error occurred reading the data
   */
  static boolean checkLiteral(StdXMLReader reader, String literal) throws IOException {
    for (int i = 0; i < literal.length(); i++) {
      if (reader.read() != literal.charAt(i)) {
        return false;
      }
    }

    return true;
  }


  /**
   * Throws an XMLParseException to indicate that an expected string is not
   * encountered.
   *
   * @param systemID       the system ID of the data source
   * @param lineNr         the line number in the data source
   * @param expectedString the string that is expected
   */
  static void errorExpectedInput(String systemID, int lineNr, String expectedString) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "Expected: " + expectedString);
  }


  /**
   * Throws an XMLParseException to indicate that an entity could not be
   * resolved.
   *
   * @param systemID the system ID of the data source
   * @param lineNr   the line number in the data source
   * @param entity   the name of the entity
   */
  static void errorInvalidEntity(String systemID, int lineNr, String entity) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "Invalid entity: `&" + entity + ";'");
  }


  /**
   * Throws an XMLParseException to indicate that an entity reference is
   * unexpected at this point.
   *
   * @param systemID the system ID of the data source
   * @param lineNr   the line number in the data source
   * @param entity   the name of the entity
   */
  static void errorUnexpectedEntity(String systemID, int lineNr, String entity) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "No entity reference is expected here (" + entity + ")");
  }


  /**
   * Throws an XMLParseException to indicate that a CDATA section is
   * unexpected at this point.
   *
   * @param systemID the system ID of the data source
   * @param lineNr   the line number in the data source
   */
  static void errorUnexpectedCDATA(String systemID, int lineNr) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "No CDATA section is expected here");
  }


  /**
   * Throws an XMLParseException to indicate that a string is not expected
   * at this point.
   *
   * @param systemID         the system ID of the data source
   * @param lineNr           the line number in the data source
   * @param unexpectedString the string that is unexpected
   */
  static void errorInvalidInput(String systemID, int lineNr, String unexpectedString) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "Invalid input: " + unexpectedString);
  }


  /**
   * Throws an XMLParseException to indicate that the closing tag of an
   * element does not match the opening tag.
   *
   * @param systemID     the system ID of the data source
   * @param lineNr       the line number in the data source
   * @param expectedName the name of the opening tag
   * @param wrongName    the name of the closing tag
   */
  static void errorWrongClosingTag(String systemID, int lineNr, String expectedName, String wrongName) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "Closing tag does not match opening tag: `" + wrongName + "' != `" + expectedName + "'");
  }


  /**
   * Throws an XMLParseException to indicate that extra data is encountered
   * in a closing tag.
   *
   * @param systemID the system ID of the data source
   * @param lineNr   the line number in the data source
   */
  static void errorClosingTagNotEmpty(String systemID, int lineNr) throws XMLParseException {
    throw new XMLParseException(systemID, lineNr, "Closing tag must be empty");
  }
}
