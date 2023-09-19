/* IXMLReader.java                                                 NanoXML/Java
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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;


/**
 * IXMLReader reads the data to be parsed.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 */
public interface IXMLReader
{

   /**
    * Reads a character.
    *
    * @return the character
    *
    * @throws java.io.IOException
    *		If no character could be read.
    */
   public char read()
      throws IOException;


   /**
    * Returns true if the current stream has no more characters left to be
    * read.
    *
    * @throws java.io.IOException
    *		If an I/O error occurred.
    */
   public boolean atEOFOfCurrentStream()
      throws IOException;


   /**
    * Returns true if there are no more characters left to be read.
    *
    * @throws java.io.IOException
    *		If an I/O error occurred.
    */
   public boolean atEOF()
      throws IOException;


   /**
    * Pushes the last character read back to the stream.
    *
    * @param ch the character to push back.
    *
    * @throws java.io.IOException
    *		If an I/O error occurred.
    */
   public void unread(char ch)
      throws IOException;


   /**
    * Returns the line number of the data in the current stream.
    */
   public int getLineNr();


   /**
    * Opens a stream from a public and system ID.
    *
    * @param publicID the public ID, which may be null.
    * @param systemID the system ID, which is never null.
    *
    * @throws java.net.MalformedURLException
    *     If the system ID does not contain a valid URL.
    * @throws java.io.FileNotFoundException
    *     If the system ID refers to a local file which does not exist.
    * @throws java.io.IOException
    *     If an error occurred opening the stream.
    */
   public Reader openStream(String publicID,
                            String systemID)
      throws MalformedURLException,
             FileNotFoundException,
             IOException;


   /**
    * Starts a new stream from a Java reader. The new stream is used
    * temporary to read data from. If that stream is exhausted, control
    * returns to the "parent" stream.
    *
    * @param reader the reader to read the new data from.
    */
   public void startNewStream(Reader reader);


   /**
    * Starts a new stream from a Java reader. The new stream is used
    * temporary to read data from. If that stream is exhausted, control
    * returns to the parent stream.
    *
    * @param reader the non-null reader to read the new data from
    * @param isInternalEntity true if the reader is produced by resolving
    *                         an internal entity
    */
   public void startNewStream(Reader  reader,
                              boolean isInternalEntity);


   /**
    * Returns the current "level" of the stream on the stack of streams.
    */
   public int getStreamLevel();


   /**
    * Sets the system ID of the current stream.
    *
    * @param systemID the system ID.
    *
    * @throws java.net.MalformedURLException
    *     If the system ID does not contain a valid URL.
    */
   public void setSystemID(String systemID)
      throws MalformedURLException;


   /**
    * Sets the public ID of the current stream.
    *
    * @param publicID the public ID.
    */
   public void setPublicID(String publicID);


   /**
    * Returns the current system ID.
    */
   public String getSystemID();


   /**
    * Returns the current public ID.
    */
   public String getPublicID();

}
