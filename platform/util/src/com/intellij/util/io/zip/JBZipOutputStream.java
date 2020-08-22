// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.io.zip;


import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.*;

class JBZipOutputStream {
  /**
   * Default compression level for deflated entries.
   */
  public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

  /**
   * The file comment.
   */
  private String comment = "";

  private int level = DEFAULT_COMPRESSION;
  private int method = ZipEntry.STORED;

  private final CRC32 crc = new CRC32();

  private long writtenOnDisk;

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   */
  private String encoding = null;

  /**
   * This Deflater object is used for output.
   * <p/>
   * <p>This attribute is only protected to provide a level of API
   * backwards compatibility.  This class used to extend {@link
   * DeflaterOutputStream DeflaterOutputStream} up to
   * Revision 1.13.</p>
   */
  private final Deflater def = new Deflater(level, true);

  /**
   * Optional random access output.
   */
  private final RandomAccessFile raf;
  private final JBZipFile myFile;

  /**
   * Creates a new ZIP OutputStream writing to a File.  Will use
   * random access if possible.
   *
   * @param file the file to zip to
   * @param currentCDOffset
   */
  JBZipOutputStream(JBZipFile file, long currentCDOffset) {
    myFile = file;
    raf = myFile.archive;
    writtenOnDisk = currentCDOffset;
  }

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   *
   * @param encoding the encoding value
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * The encoding to use for filenames and the file comment.
   *
   * @return null if using the platform's default character encoding.
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * Finishes writing the contents and closes this as well as the
   * underlying stream.
   *
   * @throws IOException on error
   */
  public void finish() throws IOException {
    long cdOffset = getWritten();
    final List<JBZipEntry> entries = myFile.getEntries();
    for (JBZipEntry entry : entries) {
      writeCentralFileHeader(entry);
    }
    long cdLength = getWritten() - cdOffset;
    writeCentralDirectoryEnd(cdLength, cdOffset);
    flushBuffer();
    def.end();
  }

  /**
   * Set the file comment.
   *
   * @param comment the comment
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
   * @param method an {@code int} from java.util.zip.ZipEntry
   */
  public void setMethod(int method) {
    this.method = method;
  }

  /*
  * Various ZIP constants
  */
  /**
   * local file header signature
   */
  protected static final byte[] LFH_SIG = ZipLong.getBytes(0X04034B50L);

  /**
   * central file header signature
   */
  protected static final byte[] CFH_SIG = ZipLong.getBytes(0X02014B50L);
  /**
   * end of central dir signature
   */
  protected static final byte[] EOCD_SIG = ZipLong.getBytes(0X06054B50L);

  /**
   * Writes the local file header entry
   *
   * @param ze the entry to write
   * @throws IOException on error
   */
  protected void writeLocalFileHeader(JBZipEntry ze) throws IOException {
    ze.setHeaderOffset(getWritten());

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

  private void updateLocalFileHeader(JBZipEntry ze, long crc, long compressedSize) throws IOException {
    ze.setCrc(crc);
    ze.setCompressedSize(compressedSize);
    flushBuffer();
    long offset = ze.getHeaderOffset() + JBZipFile.LFH_OFFSET_FOR_CRC;
    raf.seek(offset);
    raf.write(ZipLong.getBytes(crc));
    raf.write(ZipLong.getBytes(compressedSize));
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
   */
  protected byte[] getBytes(String name) throws ZipException {
    if (encoding == null) {
      return name.getBytes(StandardCharsets.UTF_8);
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
   */
  private void writeOut(byte[] data) throws IOException {
    writeOut(data, 0, data.length);
  }

  private final BufferExposingByteArrayOutputStream myBuffer = new BufferExposingByteArrayOutputStream();

  /**
   * Write bytes to output or random access file.
   *
   * @param data   the byte array to write
   * @param offset the start position to write from
   * @param length the number of bytes to write
   * @throws IOException on error
   */
  private void writeOut(byte[] data, int offset, int length) throws IOException {
    myBuffer.write(data, offset, length);
    if (myBuffer.size() > 8192) {
      flushBuffer();
    }
  }

  void ensureFlushed(long end) throws IOException {
    if (end > writtenOnDisk) flushBuffer();
  }

  private void flushBuffer() throws IOException {
    raf.seek(writtenOnDisk);
    raf.write(myBuffer.getInternalBuffer(), 0, myBuffer.size());
    writtenOnDisk += myBuffer.size();
    myBuffer.reset();
  }

  public void putNextEntryBytes(JBZipEntry entry, byte[] bytes) throws IOException {
    prepareNextEntry(entry);

    crc.reset();
    crc.update(bytes);
    entry.setCrc(crc.getValue());

    final byte[] outputBytes;
    final int outputBytesLength;
    if (entry.getMethod() == ZipEntry.DEFLATED) {
      def.setLevel(level);
      final BufferExposingByteArrayOutputStream compressedBytesStream = new BufferExposingByteArrayOutputStream();
      try (DeflaterOutputStream stream = new DeflaterOutputStream(compressedBytesStream, def)) {
        stream.write(bytes);
      }
      outputBytesLength = compressedBytesStream.size();
      outputBytes = compressedBytesStream.getInternalBuffer();
    }
    else {
      outputBytesLength = bytes.length;
      outputBytes = bytes;
    }

    entry.setCompressedSize(outputBytesLength);
    entry.setSize(bytes.length);
    writeLocalFileHeader(entry);
    writeOut(outputBytes, 0, outputBytesLength);
  }

  void putNextEntryContent(JBZipEntry entry, InputStream content) throws IOException {
    prepareNextEntry(entry);
    writeLocalFileHeader(entry);
    flushBuffer();

    RandomAccessFileOutputStream fileOutput = new RandomAccessFileOutputStream(raf);
    OutputStream bufferedFileOutput = new BufferedOutputStream(fileOutput);

    OutputStream output;
    if (entry.getMethod() == ZipEntry.DEFLATED) {
      def.setLevel(level);
      output = new DeflaterOutputStream(bufferedFileOutput, def);
    }
    else {
      output = bufferedFileOutput;
    }

    long writtenSize = 0;
    try {
      final byte[] buffer = new byte[10 * 1024];
      int count;
      crc.reset();
      while ((count = content.read(buffer)) > 0) {
        writtenSize += count;
        output.write(buffer, 0, count);
        crc.update(buffer, 0, count);
      }
    }
    finally {
      output.close();
    }
    writtenOnDisk += fileOutput.myWrittenBytes;

    entry.setSize(writtenSize);
    updateLocalFileHeader(entry, crc.getValue(), fileOutput.myWrittenBytes);
  }

  private void prepareNextEntry(JBZipEntry entry) {
    if (entry.getMethod() == -1) {
      entry.setMethod(method);
    }

    if (entry.getTime() == -1) {
      entry.setTime(System.currentTimeMillis());
    }
  }

  long getWritten() {
    return writtenOnDisk + myBuffer.size();
  }

  private static class RandomAccessFileOutputStream extends OutputStream {
    private final RandomAccessFile myFile;
    private long myWrittenBytes;

    RandomAccessFileOutputStream(RandomAccessFile file) {
      myFile = file;
    }

    @Override
    public void write(int b) throws IOException {
      myFile.write(b);
      myWrittenBytes++;
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
      myFile.write(b, off, len);
      myWrittenBytes += len;
    }
  }
}
