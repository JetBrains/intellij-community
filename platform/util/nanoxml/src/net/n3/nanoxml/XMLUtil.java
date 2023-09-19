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
class XMLUtil
{

   /**
    * Skips the remainder of a comment.
    * It is assumed that &lt;!- is already read.
    *
    * @param reader the reader
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static void skipComment(IXMLReader reader)
      throws IOException,
             XMLParseException
   {
      if (reader.read() != '-') {
         XMLUtil.errorExpectedInput(reader.getSystemID(),
                                    reader.getLineNr(),
                                    "<!--");
      }
      
      int dashesRead = 0;

      for (;;) {
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
    * @param reader         the reader
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static void skipTag(IXMLReader reader)
      throws IOException,
             XMLParseException
   {
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
    * @param publicID       will contain the public ID
    * @param reader         the reader
    *
    * @return the system ID
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static String scanPublicID(StringBuffer publicID,
                              IXMLReader   reader)
      throws IOException,
             XMLParseException
   {
      if (! XMLUtil.checkLiteral(reader, "UBLIC")) {
         return null;
      }

      XMLUtil.skipWhitespace(reader, null);
      publicID.append(XMLUtil.scanString(reader, '\0', null));
      XMLUtil.skipWhitespace(reader, null);
      return XMLUtil.scanString(reader, '\0', null);
   }


   /**
    * Scans a system ID.
    *
    * @param reader         the reader
    *
    * @return the system ID
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static String scanSystemID(IXMLReader reader)
      throws IOException,
            XMLParseException
   {
      if (! XMLUtil.checkLiteral(reader, "YSTEM")) {
         return null;
      }

      XMLUtil.skipWhitespace(reader, null);
      return XMLUtil.scanString(reader, '\0', null);
   }


   /**
    * Retrieves an identifier from the data.
    *
    * @param reader         the reader
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static String scanIdentifier(IXMLReader reader)
      throws IOException,
             XMLParseException
   {
      StringBuffer result = new StringBuffer();

      for (;;) {
         char ch = reader.read();

         if ((ch == '_') || (ch == ':') || (ch == '-') || (ch == '.')
             || ((ch >= 'a') && (ch <= 'z'))
             || ((ch >= 'A') && (ch <= 'Z'))
             || ((ch >= '0') && (ch <= '9')) || (ch > '\u007E')) {
            result.append(ch);
         } else {
            reader.unread(ch);
            break;
         }
      }

      return result.toString();
   }


   /**
    * Retrieves a delimited string from the data.
    *
    * @param reader              the reader
    * @param entityChar          the escape character (&amp; or %)
    * @param entityResolver      the entity resolver
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static String scanString(IXMLReader         reader,
                            char               entityChar,
                            IXMLEntityResolver entityResolver)
      throws IOException,
             XMLParseException
   {
      StringBuffer result = new StringBuffer();
      int startingLevel = reader.getStreamLevel();
      char delim = reader.read();

      if ((delim != '\'') && (delim != '"')) {
         XMLUtil.errorExpectedInput(reader.getSystemID(),
                                    reader.getLineNr(),
                                    "delimited string");
      }

      for (;;) {
         String str = XMLUtil.read(reader, entityChar);
         char ch = str.charAt(0);

         if (ch == entityChar) {
            if (str.charAt(1) == '#') {
               result.append(XMLUtil.processCharLiteral(str));
            } else {
               XMLUtil.processEntity(str, reader, entityResolver);
            }
         } else if (ch == '&') {
            reader.unread(ch);
            str = XMLUtil.read(reader, '&');
            if (str.charAt(1) == '#') {
               result.append(XMLUtil.processCharLiteral(str));
            } else {
               result.append(str);
            }
         } else if (reader.getStreamLevel() == startingLevel) {
            if (ch == delim) {
               break;
            } else if ((ch == 9) || (ch == 10) || (ch == 13)) {
               result.append(' ');
            } else {
               result.append(ch);
            }
         } else {
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
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static void processEntity(String             entity,
                             IXMLReader         reader,
                             IXMLEntityResolver entityResolver)
      throws IOException,
             XMLParseException
   {
      entity = entity.substring(1, entity.length() - 1);
      Reader entityReader = entityResolver.getEntity(reader, entity);

      if (entityReader == null) {
         XMLUtil.errorInvalidEntity(reader.getSystemID(),
                                    reader.getLineNr(),
                                    entity);
      }

      boolean externalEntity = entityResolver.isExternalEntity(entity);
      reader.startNewStream(entityReader, !externalEntity);
   }


   /**
    * Processes a character literal.
    *
    * @param entity         the entity
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static char processCharLiteral(String entity)
      throws IOException,
             XMLParseException
   {
      if (entity.charAt(2) == 'x') {
         entity = entity.substring(3, entity.length() - 1);
         return (char) Integer.parseInt(entity, 16);
      } else {
         entity = entity.substring(2, entity.length() - 1);
         return (char) Integer.parseInt(entity, 10);
      }
   }
   

   /**
    * Skips whitespace from the reader.
    *
    * @param reader         the reader
    * @param buffer         where to put the whitespace; null if the
    *                       whitespace does not have to be stored.
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static void skipWhitespace(IXMLReader   reader,
                              StringBuffer buffer)
      throws IOException
   {
      char ch;

      if (buffer == null) {
         do {
            ch = reader.read();
         } while ((ch == ' ') || (ch == '\t') || (ch == '\n'));
      } else {
         for (;;) {
            ch = reader.read();

            if ((ch != ' ') && (ch != '\t') && (ch != '\n')) {
               break;
            }

            if (ch == '\n') {
               buffer.append('\n');
            } else {
               buffer.append(' ');
            }
         }
      }

      reader.unread(ch);
   }
   

   /**
    * Reads a character from the reader.
    *
    * @param reader         the reader
    * @param entityChar     the escape character (&amp; or %) used to indicate
    *                       an entity
    *
    * @return the character, or an entity expression (like e.g. &amp;lt;)
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static String read(IXMLReader         reader,
                      char               entityChar)
      throws IOException,
             XMLParseException
   {
      char ch = reader.read();
      StringBuffer buf = new StringBuffer();
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
    * @param reader         the reader
    * @param entityChar     the escape character (&amp; or %) used to indicate
    *                       an entity
    */
   static char readChar(IXMLReader reader,
                        char       entityChar)
      throws IOException,
             XMLParseException
   {
      String str = XMLUtil.read(reader, entityChar);
      char ch = str.charAt(0);

      if (ch == entityChar) {
         XMLUtil.errorUnexpectedEntity(reader.getSystemID(),
                                       reader.getLineNr(),
                                       str);
      }

      return ch;
   }


   /**
    * Returns true if the data starts with <I>literal</I>.
    * Enough chars are read to determine this result.
    *
    * @param reader         the reader
    * @param literal        the literal to check
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   static boolean checkLiteral(IXMLReader         reader,
                               String             literal)
      throws IOException,
             XMLParseException
   {
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
   static void errorExpectedInput(String systemID,
                                  int    lineNr,
                                  String expectedString)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "Expected: " + expectedString);
   }


   /**
    * Throws an XMLParseException to indicate that an entity could not be
    * resolved.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param entity    the name of the entity
    */
   static void errorInvalidEntity(String systemID,
                                  int    lineNr,
                                  String     entity)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "Invalid entity: `&" + entity + ";'");
   }


   /**
    * Throws an XMLParseException to indicate that an entity reference is
    * unexpected at this point.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param entity    the name of the entity
    */
   static void errorUnexpectedEntity(String systemID,
                                     int    lineNr,
                                     String entity)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "No entity reference is expected here ("
                                  + entity + ")");
   }


   /**
    * Throws an XMLParseException to indicate that a CDATA section is
    * unexpected at this point.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    */
   static void errorUnexpectedCDATA(String systemID,
                                    int    lineNr)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "No CDATA section is expected here");
   }


   /**
    * Throws an XMLParseException to indicate that a string is not expected
    * at this point.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param unexpectedString the string that is unexpected
    */
   static void errorInvalidInput(String systemID,
                                 int    lineNr,
                                 String     unexpectedString)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "Invalid input: " + unexpectedString);
   }


   /**
    * Throws an XMLParseException to indicate that the closing tag of an
    * element does not match the opening tag.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param expectedName the name of the opening tag
    * @param wrongName    the name of the closing tag
    */
   static void errorWrongClosingTag(String systemID,
                                    int    lineNr,
                                    String     expectedName,
                                    String     wrongName)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "Closing tag does not match opening tag: `"
                                  + wrongName + "' != `" + expectedName
                                  + "'");
   }


   /**
    * Throws an XMLParseException to indicate that extra data is encountered
    * in a closing tag.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    */
   static void errorClosingTagNotEmpty(String systemID,
                                       int    lineNr)
      throws XMLParseException
   {
      throw new XMLParseException(systemID, lineNr,
                                  "Closing tag must be empty");
   }


   /**
    * Throws an XMLValidationException to indicate that an element is missing.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param parentElementName the name of the parent element
    * @param missingElementName the name of the missing element
    */
   static void errorMissingElement(String systemID,
                                   int    lineNr,
                                   String parentElementName,
                                   String missingElementName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                              XMLValidationException.MISSING_ELEMENT,
                              systemID, lineNr,
                              missingElementName,
                              /*attributeName*/ null,
                              /*attributeValue*/ null,
                              "Element " + parentElementName
                              + " expects to have a " + missingElementName);
   }


   /**
    * Throws an XMLValidationException to indicate that an element is
    * unexpected.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param parentElementName the name of the parent element
    * @param unexpectedElementName the name of the unexpected element
    */
   static void errorUnexpectedElement(String systemID,
                                      int    lineNr,
                                      String parentElementName,
                                      String unexpectedElementName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                              XMLValidationException.UNEXPECTED_ELEMENT,
                              systemID, lineNr,
                              unexpectedElementName,
                              /*attributeName*/ null,
                              /*attributeValue*/ null,
                              "Unexpected " + unexpectedElementName + " in a "
                              + parentElementName);
   }


   /**
    * Throws an XMLValidationException to indicate that an attribute is
    * missing.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param elementName    the name of the element
    * @param attributeName  the name of the missing attribute
    */
   static void errorMissingAttribute(String systemID,
                                     int    lineNr,
                                     String elementName,
                                     String attributeName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                     XMLValidationException.MISSING_ATTRIBUTE,
                     systemID, lineNr,
                     elementName,
                     attributeName,
                     /*attributeValue*/ null,
                     "Element " + elementName + " expects an attribute named "
                     + attributeName);
   }


   /**
    * Throws an XMLValidationException to indicate that an attribute is
    * unexpected.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param elementName    the name of the element
    * @param attributeName  the name of the unexpected attribute
    */
   static void errorUnexpectedAttribute(String systemID,
                                        int    lineNr,
                                        String elementName,
                                        String attributeName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                     XMLValidationException.UNEXPECTED_ATTRIBUTE,
                     systemID, lineNr,
                     elementName,
                     attributeName,
                     /*attributeValue*/ null,
                     "Element " + elementName + " did not expect an attribute "
                     + "named " + attributeName);
   }


   /**
    * Throws an XMLValidationException to indicate that an attribute has an
    * invalid value.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param elementName    the name of the element
    * @param attributeName  the name of the attribute
    * @param attributeValue the value of that attribute
    */
   static void errorInvalidAttributeValue(String systemID,
                                          int    lineNr,
                                          String elementName,
                                          String attributeName,
                                          String attributeValue)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                           XMLValidationException.ATTRIBUTE_WITH_INVALID_VALUE,
                           systemID, lineNr,
                           elementName,
                           attributeName,
                           attributeValue,
                           "Invalid value for attribute " + attributeName);
   }


   /**
    * Throws an XMLValidationException to indicate that a #PCDATA element was
    * missing.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param parentElementName the name of the parent element
    */
   static void errorMissingPCData(String systemID,
                                  int    lineNr,
                                  String parentElementName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                           XMLValidationException.MISSING_PCDATA,
                           systemID, lineNr,
                           /*elementName*/ null,
                           /*attributeName*/ null,
                           /*attributeValue*/ null,
                           "Missing #PCDATA in element " + parentElementName);
   }
   

   /**
    * Throws an XMLValidationException to indicate that a #PCDATA element was
    * unexpected.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param parentElementName the name of the parent element
    */
   static void errorUnexpectedPCData(String systemID,
                                     int    lineNr,
                                     String parentElementName)
      throws XMLValidationException
   {
      throw new XMLValidationException(
                        XMLValidationException.UNEXPECTED_PCDATA,
                        systemID, lineNr,
                        /*elementName*/ null,
                        /*attributeName*/ null,
                        /*attributeValue*/ null,
                        "Unexpected #PCDATA in element " + parentElementName);
   }


   /**
    * Throws an XMLValidationException.
    *
    * @param systemID       the system ID of the data source
    * @param lineNr         the line number in the data source
    * @param message        the error message
    * @param elementName    the name of the element
    * @param attributeName  the name of the attribute
    * @param attributeValue the value of that attribute
    */
   static void validationError(String systemID,
                               int    lineNr,
                               String message,
                               String elementName,
                               String attributeName,
                               String attributeValue)
      throws XMLValidationException
   {
      throw new XMLValidationException(XMLValidationException.MISC_ERROR,
                                       systemID, lineNr,
                                       elementName,
                                       attributeName,
                                       attributeValue,
                                       message);
   }

}
