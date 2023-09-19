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
class ContentReader
   extends Reader
{

   /**
    * The encapsulated reader.
    */
   private IXMLReader reader;


   /**
    * Buffer.
    */
   private String buffer;


   /**
    * Pointer into the buffer.
    */
   private int bufferIndex;


   /**
    * The entity resolver.
    */
   private IXMLEntityResolver resolver;
    

   /**
    * Creates the reader.
    *
    * @param reader the encapsulated reader
    * @param resolver the entity resolver
    * @param buffer data that has already been read from <code>reader</code>
    */
   ContentReader(IXMLReader         reader,
                 IXMLEntityResolver resolver,
                 String             buffer)
   {
      this.reader = reader;
      this.resolver = resolver;
      this.buffer = buffer;
      this.bufferIndex = 0;
   }


   /**
    * Cleans up the object when it's destroyed.
    */
   protected void finalize()
      throws Throwable
   {
      this.reader = null;
      this.resolver = null;
      this.buffer = null;
      super.finalize();
   }


   /**
    * Reads a block of data.
    *
    * @param outputBuffer where to put the read data
    * @param offset first position in buffer to put the data
    * @param size maximum number of chars to read
    *
    * @return the number of chars read, or -1 if at EOF
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   public int read(char[] outputBuffer,
                   int    offset,
                   int    size)
      throws IOException
   {
      try {
         int charsRead = 0;
         int bufferLength = this.buffer.length();

         if ((offset + size) > outputBuffer.length) {
            size = outputBuffer.length - offset;
         }

         while (charsRead < size) {
            String str = "";
            char ch;

            if (this.bufferIndex >= bufferLength) {
               str = XMLUtil.read(this.reader, '&');
               ch = str.charAt(0);
            } else {
               ch = this.buffer.charAt(this.bufferIndex);
               this.bufferIndex++;
               outputBuffer[charsRead] = ch;
               charsRead++;
               continue; // don't interprete chars in the buffer
            }

            if (ch == '<') {
               this.reader.unread(ch);
               break;
            }

            if ((ch == '&') && (str.length() > 1)) {
               if (str.charAt(1) == '#') {
                  ch = XMLUtil.processCharLiteral(str);
               } else {
                  XMLUtil.processEntity(str, this.reader, this.resolver);
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
      } catch (XMLParseException e) {
         throw new IOException(e.getMessage());
      }
   }


   /**
    * Skips remaining data and closes the stream.
    *
    * @throws java.io.IOException
    *		if an error occurred reading the data
    */
   public void close()
      throws IOException
   {
      try {
         int bufferLength = this.buffer.length();

         for (;;) {
            String str = "";
            char ch;

            if (this.bufferIndex >= bufferLength) {
               str = XMLUtil.read(this.reader, '&');
               ch = str.charAt(0);
            } else {
               ch = this.buffer.charAt(this.bufferIndex);
               this.bufferIndex++;
               continue; // don't interprete chars in the buffer
            }

            if (ch == '<') {
               this.reader.unread(ch);
               break;
            }

            if ((ch == '&') && (str.length() > 1)) {
               if (str.charAt(1) != '#') {
                  XMLUtil.processEntity(str, this.reader, this.resolver);
               }
            }
         }
      } catch (XMLParseException e) {
         throw new IOException(e.getMessage());
      }
   }

}
