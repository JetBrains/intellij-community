/* StdXMLReader.java                                               NanoXML/Java
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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * StdXMLReader reads the data to be parsed.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 */
public class StdXMLReader {

  /**
   * The stack of readers.
   */
  private final Deque<StackedReader> readers;
  /**
   * The current push-back reader.
   */
  private StackedReader currentReader;


  /**
   * Initializes the reader from a system and public ID.
   *
   * @param publicID the public ID which may be null.
   * @param systemID the non-null system ID.
   * @throws MalformedURLException if the system ID does not contain a valid URL
   * @throws FileNotFoundException if the system ID refers to a local file which does not exist
   * @throws IOException           if an error occurred while opening the stream
   */
  public StdXMLReader(String publicID, String systemID) throws MalformedURLException, FileNotFoundException, IOException {
    URL systemIDasURL;

    try {
      systemIDasURL = new URL(systemID);
    }
    catch (MalformedURLException e) {
      systemID = "file:" + systemID;

      try {
        systemIDasURL = new URL(systemID);
      }
      catch (MalformedURLException e2) {
        throw e;
      }
    }

    currentReader = new StackedReader();
    readers = new ArrayDeque<>();
    Reader reader = openStream(publicID, systemIDasURL.toString());
    currentReader.lineReader = new LineNumberReader(reader);
    currentReader.pbReader = new PushbackReader(currentReader.lineReader, 2);
  }


  /**
   * Initializes the XML reader.
   *
   * @param reader the input for the XML data.
   */
  public StdXMLReader(Reader reader) {
    currentReader = new StackedReader();
    readers = new ArrayDeque<>();
    currentReader.lineReader = new LineNumberReader(reader);
    currentReader.pbReader = new PushbackReader(currentReader.lineReader, 2);
    currentReader.publicId = "";

    try {
      currentReader.systemId = new URL("file:.");
    }
    catch (MalformedURLException e) {
      // never happens
    }
  }


  /**
   * Initializes the XML reader.
   *
   * @param stream the input for the XML data.
   * @throws IOException if an I/O error occurred
   */
  public StdXMLReader(InputStream stream) throws IOException {
    PushbackInputStream pbstream = new PushbackInputStream(stream);
    StringBuilder charsRead = new StringBuilder();
    Reader reader = stream2reader(stream, charsRead);
    currentReader = new StackedReader();
    readers = new ArrayDeque<>();
    currentReader.lineReader = new LineNumberReader(reader);
    currentReader.pbReader = new PushbackReader(currentReader.lineReader, 2);
    currentReader.publicId = "";

    try {
      currentReader.systemId = new URL("file:.");
    }
    catch (MalformedURLException e) {
      // never happens
    }

    startNewStream(new StringReader(charsRead.toString()));
  }

  /**
   * Scans the encoding from an {@code <?xml...?>} tag.
   *
   * @param str the first tag in the XML data.
   * @return the encoding, or null if no encoding has been specified.
   */
  protected String getEncoding(String str) {
    if (!str.startsWith("<?xml")) {
      return null;
    }

    int index = 5;

    while (index < str.length()) {
      StringBuilder key = new StringBuilder();

      while ((index < str.length()) && (str.charAt(index) <= ' ')) {
        index++;
      }

      while ((index < str.length()) && (str.charAt(index) >= 'a') && (str.charAt(index) <= 'z')) {
        key.append(str.charAt(index));
        index++;
      }

      while ((index < str.length()) && (str.charAt(index) <= ' ')) {
        index++;
      }

      if ((index >= str.length()) || (str.charAt(index) != '=')) {
        break;
      }

      while ((index < str.length()) && (str.charAt(index) != '\'') && (str.charAt(index) != '"')) {
        index++;
      }

      if (index >= str.length()) {
        break;
      }

      char delimiter = str.charAt(index);
      index++;
      int index2 = str.indexOf(delimiter, index);

      if (index2 < 0) {
        break;
      }

      if (key.toString().equals("encoding")) {
        return str.substring(index, index2);
      }

      index = index2 + 1;
    }

    return null;
  }

  /**
   * Converts a stream to a reader while detecting the encoding.
   *
   * @param stream    the input for the XML data.
   * @param charsRead buffer where to put characters that have been read
   * @throws IOException if an I/O error occurred
   */
  protected Reader stream2reader(InputStream stream, StringBuilder charsRead) throws IOException {
    PushbackInputStream pbstream = new PushbackInputStream(stream);
    int b = pbstream.read();

    switch (b) {
      case 0x00:
      case 0xFE:
      case 0xFF:
        pbstream.unread(b);
        return new InputStreamReader(pbstream, StandardCharsets.UTF_16);

      case 0xEF:
        for (int i = 0; i < 2; i++) {
          if (pbstream.read() < 0) {
            throw new IOException("Unexpected end of file");
          }
        }

        return new InputStreamReader(pbstream, StandardCharsets.UTF_8);

      case 0x3C:
        b = pbstream.read();
        charsRead.append('<');

        while ((b > 0) && (b != 0x3E)) {
          charsRead.append((char)b);
          b = pbstream.read();
        }

        if (b > 0) {
          charsRead.append((char)b);
        }

        String encoding = getEncoding(charsRead.toString());

        if (encoding == null) {
          return new InputStreamReader(pbstream, StandardCharsets.UTF_8);
        }

        charsRead.setLength(0);

        try {
          return new InputStreamReader(pbstream, encoding);
        }
        catch (UnsupportedEncodingException e) {
          return new InputStreamReader(pbstream, StandardCharsets.UTF_8);
        }

      default:
        charsRead.append((char)b);
        return new InputStreamReader(pbstream, StandardCharsets.UTF_8);
    }
  }

  /**
   * Reads a character.
   *
   * @return the character
   * @throws IOException if no character could be read
   */
  public char read() throws IOException {
    int ch = currentReader.pbReader.read();

    while (ch < 0) {
      if (readers.isEmpty()) {
        throw new IOException("Unexpected EOF");
      }

      currentReader.pbReader.close();
      currentReader = readers.pop();
      ch = currentReader.pbReader.read();
    }

    return (char)ch;
  }

  /**
   * Returns true if there are no more characters left to be read.
   *
   * @throws IOException if an I/O error occurred
   */
  public boolean atEOF() throws IOException {
    int ch = currentReader.pbReader.read();

    while (ch < 0) {
      if (readers.isEmpty()) {
        return true;
      }

      currentReader.pbReader.close();
      currentReader = readers.pop();
      ch = currentReader.pbReader.read();
    }

    currentReader.pbReader.unread(ch);
    return false;
  }

  /**
   * Pushes the last character read back to the stream.
   *
   * @param ch the character to push back.
   * @throws IOException if an I/O error occurred
   */
  public void unread(char ch) throws IOException {
    currentReader.pbReader.unread(ch);
  }

  /**
   * Opens a stream from a public and system ID.
   *
   * @param publicID the public ID, which may be null
   * @param systemID the system ID, which is never null
   * @throws MalformedURLException if the system ID does not contain a valid URL
   * @throws FileNotFoundException if the system ID refers to a local file which does not exist
   * @throws IOException           if an error occurred while opening the stream
   */
  public Reader openStream(String publicID, String systemID) throws MalformedURLException, FileNotFoundException, IOException {
    URL url = new URL(currentReader.systemId, systemID);

    if (url.getRef() != null) {
      String ref = url.getRef();

      if (!url.getFile().isEmpty()) {
        url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile());
        url = new URL("jar:" + url + '!' + ref);
      }
      else {
        url = StdXMLReader.class.getResource(ref);
      }
    }

    currentReader.publicId = publicID;
    currentReader.systemId = url;
    StringBuilder charsRead = new StringBuilder();
    Reader reader = stream2reader(url.openStream(), charsRead);

    if (charsRead.length() == 0) {
      return reader;
    }

    String charsReadStr = charsRead.toString();
    PushbackReader pbreader = new PushbackReader(reader, charsReadStr.length());

    for (int i = charsReadStr.length() - 1; i >= 0; i--) {
      pbreader.unread(charsReadStr.charAt(i));
    }

    return pbreader;
  }

  /**
   * Starts a new stream from a Java reader. The new stream is used
   * temporary to read data from. If that stream is exhausted, control
   * returns to the parent stream.
   *
   * @param reader the non-null reader to read the new data from
   */
  public void startNewStream(Reader reader) {
    startNewStream(reader, false);
  }

  /**
   * Starts a new stream from a Java reader. The new stream is used
   * temporary to read data from. If that stream is exhausted, control
   * returns to the parent stream.
   *
   * @param reader           the non-null reader to read the new data from
   * @param isInternalEntity true if the reader is produced by resolving
   *                         an internal entity
   */
  public void startNewStream(Reader reader, boolean isInternalEntity) {
    StackedReader oldReader = currentReader;
    readers.push(currentReader);
    currentReader = new StackedReader();

    if (isInternalEntity) {
      currentReader.lineReader = null;
      currentReader.pbReader = new PushbackReader(reader, 2);
    }
    else {
      currentReader.lineReader = new LineNumberReader(reader);
      currentReader.pbReader = new PushbackReader(currentReader.lineReader, 2);
    }

    currentReader.systemId = oldReader.systemId;
    currentReader.publicId = oldReader.publicId;
  }

  /**
   * Returns the current "level" of the stream on the stack of streams.
   */
  public int getStreamLevel() {
    return readers.size();
  }

  /**
   * Returns the line number of the data in the current stream.
   */
  public int getLineNr() {
    if (currentReader.lineReader == null) {
      StackedReader sr = readers.peek();

      if (sr.lineReader == null) {
        return 0;
      }
      else {
        return sr.lineReader.getLineNumber() + 1;
      }
    }

    return currentReader.lineReader.getLineNumber() + 1;
  }

  /**
   * Returns the current system ID.
   */
  public String getSystemID() {
    return currentReader.systemId.toString();
  }

  /**
   * Sets the system ID of the current stream.
   *
   * @param systemID the system ID
   * @throws MalformedURLException if the system ID does not contain a valid URL
   */
  public void setSystemID(String systemID) throws MalformedURLException {
    currentReader.systemId = new URL(currentReader.systemId, systemID);
  }

  /**
   * Returns the current public ID.
   */
  public String getPublicID() {
    return currentReader.publicId;
  }

  /**
   * Sets the public ID of the current stream.
   *
   * @param publicID the public ID
   */
  public void setPublicID(String publicID) {
    currentReader.publicId = publicID;
  }

  /**
   * Creates a new reader using a string as input.
   *
   * @param str the string containing the XML data
   */
  public static StdXMLReader stringReader(String str) {
    return new StdXMLReader(new StringReader(str));
  }

  /**
   * Creates a new reader using a file as input.
   *
   * @param filename the name of the file containing the XML data
   * @throws FileNotFoundException if the file could not be found
   * @throws IOException           if an I/O error occurred
   */
  public static StdXMLReader fileReader(String filename) throws FileNotFoundException, IOException {
    StdXMLReader r = new StdXMLReader(new FileInputStream(filename));
    r.setSystemID(filename);

    for (StackedReader sr : r.readers) {
      sr.systemId = r.currentReader.systemId;
    }

    return r;
  }

  /**
   * A stacked reader.
   *
   * @author Marc De Scheemaecker
   * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
   */
  private static final class StackedReader {

    PushbackReader pbReader;

    LineNumberReader lineReader;

    URL systemId;

    String publicId;
  }
}
