/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io.zip;


import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.*;

class JBZipOutputStream {
  /**
   * Default compression level for deflated entries.
   *
   * @since Ant 1.7
   */
  public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

  /**
   * The file comment.
   *
   * @since 1.1
   */
  private String comment = "";

  private int level = DEFAULT_COMPRESSION;
  private int method = ZipEntry.STORED;

  private final CRC32 crc = new CRC32();

  long written = 0;

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   *
   * @since 1.3
   */
  private String encoding = null;

  /**
   * This Deflater object is used for output.
   * <p/>
   * <p>This attribute is only protected to provide a level of API
   * backwards compatibility.  This class used to extend {@link
   * java.util.zip.DeflaterOutputStream DeflaterOutputStream} up to
   * Revision 1.13.</p>
   *
   * @since 1.14
   */
  private final Deflater def = new Deflater(level, true);

  /**
   * Optional random access output.
   *
   * @since 1.14
   */
  private final RandomAccessFile raf;
  private final JBZipFile myFile;

  /**
   * Creates a new ZIP OutputStream writing to a File.  Will use
   * random access if possible.
   *
   * @param file the file to zip to
   * @param currentCDOffset
   * @throws IOException on error
   * @since 1.14
   */
  public JBZipOutputStream(JBZipFile file, long currentCDOffset) throws IOException {
    myFile = file;
    raf = myFile.archive;
    written = currentCDOffset;
    raf.seek(currentCDOffset);
  }

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   *
   * @param encoding the encoding value
   * @since 1.3
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * The encoding to use for filenames and the file comment.
   *
   * @return null if using the platform's default character encoding.
   * @since 1.3
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * Finishs writing the contents and closes this as well as the
   * underlying stream.
   *
   * @throws IOException on error
   * @since 1.1
   */
  public void finish() throws IOException {
    long cdOffset = written;
    final List<JBZipEntry> entries = myFile.getEntries();
    for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
      writeCentralFileHeader(entries.get(i));
    }
    long cdLength = written - cdOffset;
    writeCentralDirectoryEnd(cdLength, cdOffset);
    flushBuffer();
  }

  /**
   * Set the file comment.
   *
   * @param comment the comment
   * @since 1.1
   */
  public void setComment(String comment) {
    this.comment = comment;
  }

  /**
   * Sets the compression level for subsequent entries.
   * <p/>
   * <p>Default is Deflater.DEFAULT_COMPRESSION.</p>
   *
   * @param level the compression level.
   * @throws IllegalArgumentException if an invalid compression level is specified.
   * @since 1.1
   */
  public void setLevel(int level) {
    if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
      throw new IllegalArgumentException("Invalid compression level: " + level);
    }
    this.level = level;
  }

  /**
   * Sets the default compression method for subsequent entries.
   * <p/>
   * <p>Default is DEFLATED.</p>
   *
   * @param method an <code>int</code> from java.util.zip.ZipEntry
   * @since 1.1
   */
  public void setMethod(int method) {
    this.method = method;
  }

  /*
  * Various ZIP constants
  */
  /**
   * local file header signature
   *
   * @since 1.1
   */
  protected static final byte[] LFH_SIG = ZipLong.getBytes(0X04034B50L);

  /**
   * central file header signature
   *
   * @since 1.1
   */
  protected static final byte[] CFH_SIG = ZipLong.getBytes(0X02014B50L);
  /**
   * end of central dir signature
   *
   * @since 1.1
   */
  protected static final byte[] EOCD_SIG = ZipLong.getBytes(0X06054B50L);

  /**
   * Writes the local file header entry
   *
   * @param ze the entry to write
   * @throws IOException on error
   * @since 1.1
   */
  protected void writeLocalFileHeader(JBZipEntry ze) throws IOException {
    ze.setHeaderOffset(written);

    writeOut(LFH_SIG);

    // version needed to extract
    writeOutShort(10);

    // general purpose bit flag
    writeOutShort(0);

    writeOutShort(ze.getMethod());

    writeOutLong(DosTime.javaToDosTime(ze.getTime()));

    writeOutLong(ze.getCrc());
    writeOutLong(ze.getCompressedSize());
    writeOutLong(ze.getSize());

    byte[] name = getBytes(ze.getName());
    writeOutShort(name.length);

    byte[] extra = ze.getLocalFileDataExtra();
    writeOutShort(extra.length);

    writeOut(name);

    writeOut(extra);
  }

  private void writeOutShort(int s) throws IOException {
    writeOut(ZipShort.getBytes(s));
  }

  private void writeOutLong(long s) throws IOException {
    writeOut(ZipLong.getBytes(s));
  }

  /**
   * Writes the central file header entry.
   *
   * @param ze the entry to write
   * @throws IOException on error
   * @since 1.1
   */
  protected void writeCentralFileHeader(JBZipEntry ze) throws IOException {
    writeOut(CFH_SIG);

    // version made by
    writeOutShort((ze.getPlatform() << 8) | 20);

    // version needed to extract
    writeOutShort(10);

    // general purpose bit flag
    writeOutShort(0);

    writeOutShort(ze.getMethod());

    writeOutLong(DosTime.javaToDosTime(ze.getTime()));

    writeOutLong(ze.getCrc());
    writeOutLong(ze.getCompressedSize());
    writeOutLong(ze.getSize());

    byte[] name = getBytes(ze.getName());
    writeOutShort(name.length);

    byte[] extra = ze.getExtra();
    writeOutShort(extra.length);

    String comm = ze.getComment();
    if (comm == null) {
      comm = "";
    }
    byte[] commentB = getBytes(comm);
    writeOutShort(commentB.length);

    writeOutShort(0);
    writeOutShort(ze.getInternalAttributes());
    writeOutLong(ze.getExternalAttributes());
    writeOutLong(ze.getHeaderOffset());

    writeOut(name);
    writeOut(extra);
    writeOut(commentB);
  }

  /**
   * Writes the &quot;End of central dir record&quot;.
   *
   * @throws IOException on error
   * @since 1.1
   * @param cdLength
   * @param cdOffset
   */
  protected void writeCentralDirectoryEnd(long cdLength, long cdOffset) throws IOException {
    writeOut(EOCD_SIG);

    // disk numbers
    writeOutShort(0);
    writeOutShort(0);

    // number of entries
    final int entiresCount = myFile.getEntries().size();
    writeOutShort(entiresCount);
    writeOutShort(entiresCount);

    // length and location of CD
    writeOutLong(cdLength);
    writeOutLong(cdOffset);

    // ZIP file comment
    byte[] data = getBytes(comment);
    writeOutShort(data.length);
    writeOut(data);
  }

  /**
   * Retrieve the bytes for the given String in the encoding set for
   * this Stream.
   *
   * @param name the string to get bytes from
   * @return the bytes as a byte array
   * @throws ZipException on error
   * @since 1.3
   */
  protected byte[] getBytes(String name) throws ZipException {
    if (encoding == null) {
      return name.getBytes();
    }
    else {
      try {
        return name.getBytes(encoding);
      }
      catch (UnsupportedEncodingException uee) {
        throw new ZipException(uee.getMessage());
      }
    }
  }

  /**
   * Write bytes to output or random access file.
   *
   * @param data the byte array to write
   * @throws IOException on error
   * @since 1.14
   */
  private void writeOut(byte[] data) throws IOException {
    writeOut(data, 0, data.length);
  }

  /**
   * Write bytes to output or random access file.
   *
   * @param data   the byte array to write
   * @param offset the start position to write from
   * @param length the number of bytes to write
   * @throws IOException on error
   * @since 1.14
   */
  private final BufferExposingByteArrayOutputStream myBuffer = new BufferExposingByteArrayOutputStream();

  private void writeOut(byte[] data, int offset, int length) throws IOException {
    myBuffer.write(data, offset, length);
    if (myBuffer.size() > 8192) {
      flushBuffer();
    }
    written += length;
  }

  private void flushBuffer() throws IOException {
    raf.write(myBuffer.getInternalBuffer(), 0, myBuffer.size());
    myBuffer.reset();
  }

  public void putNextEntryBytes(JBZipEntry entry, byte[] bytes) throws IOException {
    entry.setSize(bytes.length);

    crc.reset();
    crc.update(bytes);
    entry.setCrc(crc.getValue());

    if (entry.getMethod() == -1) {
      entry.setMethod(method);
    }

    if (entry.getTime() == -1) {
      entry.setTime(System.currentTimeMillis());
    }

    final byte[] outputBytes;
    final int outputBytesLength;
    if (entry.getMethod() == ZipEntry.DEFLATED) {
      def.setLevel(level);
      final BufferExposingByteArrayOutputStream compressedBytesStream = new BufferExposingByteArrayOutputStream();
      final DeflaterOutputStream stream = new DeflaterOutputStream(compressedBytesStream, def);
      try {
        stream.write(bytes);
      }
      finally {
        stream.close();
      }
      outputBytesLength = compressedBytesStream.size();
      outputBytes = compressedBytesStream.getInternalBuffer();
    }
    else {
      outputBytesLength = bytes.length;
      outputBytes = bytes;
    }

    entry.setCompressedSize(outputBytesLength);
    writeLocalFileHeader(entry);
    writeOut(outputBytes, 0, outputBytesLength);
  }
}
