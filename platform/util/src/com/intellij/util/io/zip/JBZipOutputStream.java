// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.*;

import static com.intellij.util.io.zip.JBZipFile.*;

final class JBZipOutputStream {
  private static final int ZIP64_MIN_VERSION = 45;
  private static final int ZIP_MIN_VERSION = 10;

  private static final long ZIP64_MAGIC = 0xFFFFFFFFL;

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
  private final SeekableByteChannel raf;
  private final JBZipFile myFile;

  /**
   * Creates a new ZIP OutputStream writing to a File.  Will use
   * random access if possible.
   *
   * @param file the file to zip to
   * @param currentCDOffset {@link JBZipFile#currentCfdOffset}
   */
  JBZipOutputStream(JBZipFile file, long currentCDOffset) {
    myFile = file;
    raf = myFile.myArchive;
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

    if (myFile.isZip64()) {
      writeZip64CentralDirectory(cdLength, cdOffset);
    }
    writeCentralDirectoryEnd(cdLength, cdOffset);
    flushBuffer();
    def.end();
  }

  private void writeZip64CentralDirectory(long cdLength, long cdOffset) throws IOException {
    long offset = getWritten();

    writeOut(ZIP64_EOCD_SIG);

    writeOut(ZipUInt64
               .getBytes(SHORT   /* version made by */
                         + SHORT /* version needed to extract */
                         + WORD  /* disk number */
                         + WORD  /* disk with central directory */
                         + DWORD /* number of entries in CD on this disk */
                         + DWORD /* total number of entries */
                         + DWORD /* size of CD */
                         + (long) DWORD /* offset of CD */
               ));

    // version made by
    writeOut(ZipShort.getBytes(ZIP64_MIN_VERSION));
    // version needed to extract
    writeOut(ZipShort.getBytes(ZIP64_MIN_VERSION));

    // number of this disk
    writeOut(ZipLong.getBytes(0));

    // disk number of the start of central directory
    writeOut(ZipLong.getBytes(0));

    // total number of entries in the central directory on this disk
    int numOfEntriesOnThisDisk = myFile.getEntries().size();
    final byte[] numOfEntriesOnThisDiskData = ZipUInt64.getBytes(numOfEntriesOnThisDisk);
    writeOut(numOfEntriesOnThisDiskData);

    // number of entries
    final byte[] num = ZipUInt64.getBytes(myFile.getEntries().size());
    writeOut(num);

    // length and location of CD
    writeOut(ZipUInt64.getBytes(cdLength));
    writeOut(ZipUInt64.getBytes(cdOffset));

    // no "zip64 extensible data sector"

    // and now the "ZIP64 end of central directory locator"
    writeOut(ZIP64_EOCD_LOC_SIG);

    // disk number holding the ZIP64 EOCD record
    writeOut(ZipLong.getBytes(0));
    // relative offset of ZIP64 EOCD record
    writeOut(ZipUInt64.getBytes(offset));
    // total number of disks
    writeOut(ZipLong.getBytes(1));
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
  private static final byte[] LFH_SIG = ZipLong.getBytes(0X04034B50L);

  /**
   * central file header signature
   */
  static final byte[] CFH_SIG = ZipLong.getBytes(0X02014B50L);
  /**
   * end of central dir signature
   */
  static final byte[] EOCD_SIG = ZipLong.getBytes(0X06054B50L);

  /**
   * end of zip 64 central dir locator signature
   */
  static final byte[] ZIP64_EOCD_LOC_SIG = ZipLong.getBytes(0X07064B50L);

  /**
   * end of zip 64 central dir signature
   */
  static final byte[] ZIP64_EOCD_SIG = ZipLong.getBytes(0X06064B50L);

  /**
   * Writes the local file header entry
   *
   * @param ze the entry to write
   * @throws IOException on error
   */
  private ExtraFieldData writeLocalFileHeader(JBZipEntry ze) throws IOException {
    long headerOffset = getWritten();
    ze.setHeaderOffset(headerOffset);

    if (myFile.isZip64()) {
      ze.addExtra(new Zip64ExtraField(new ZipUInt64(ze.getSize()),
                                      new ZipUInt64(ze.getCompressedSize()),
                                      new ZipUInt64(ze.getHeaderOffset())));
    }
    else if (headerOffset >= ZIP64_MAGIC) {
      throw new IOException("entry header offset is greater than maximal supported: " + headerOffset);
    }

    writeOut(LFH_SIG);

    // version needed to extract
    writeOutShort(myFile.isZip64() ? ZIP64_MIN_VERSION : ZIP_MIN_VERSION);

    // general purpose bit flag
    writeOutShort(0);

    writeOutShort(ze.getMethod());

    writeOutLong(DosTime.javaToDosTime(ze.getTime()));

    writeOutLong(ze.getCrc());

    if (ze.getCompressedSize() >= ZIP64_MAGIC) {
      throw new IOException("compressed size is greater than maximal supported: " + ze.getCompressedSize());
    }
    writeOutLong(ze.getCompressedSize());

    if (ze.getSize() >= ZIP64_MAGIC) {
      throw new IOException("size is greater than maximal supported: " + ze.getSize());
    }
    writeOutLong(ze.getSize());

    byte[] name = getBytes(ze.getName());
    writeOutShort(name.length);

    byte[] extra = ze.getLocalFileHeaderDataExtra();
    writeOutShort(extra.length);

    writeOut(name);

    long extraOffset = getWritten();
    writeOut(extra);
    return new ExtraFieldData(extraOffset, extra.length);
  }

  private void updateLocalFileHeader(JBZipEntry ze, long crc, long compressedSize, ExtraFieldData extra) throws IOException {
    ze.setCrc(crc);
    ze.setCompressedSize(compressedSize);
    if (myFile.isZip64()) {
      ze.addExtra(new Zip64ExtraField(new ZipUInt64(ze.getSize()),
                                      new ZipUInt64(ze.getCompressedSize()),
                                      new ZipUInt64(ze.getHeaderOffset())));
    }
    flushBuffer();
    long offset = ze.getHeaderOffset() + LFH_OFFSET_FOR_CRC;
    raf.position(offset);
    raf.write(ByteBuffer.wrap(ZipLong.getBytes(crc)));
    raf.write(ByteBuffer.wrap(ZipLong.getBytes(compressedSize)));
    raf.write(ByteBuffer.wrap(ZipLong.getBytes(ze.getSize())));

    raf.position(extra.offset);
    byte[] extraData = ze.getLocalFileHeaderDataExtra();
    if (extra.length != extraData.length) {
      throw new IOException("Extra data is unstable");
    }
    raf.write(ByteBuffer.wrap(extraData));
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
  private void writeCentralFileHeader(JBZipEntry ze) throws IOException {
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

    byte[] extra = ze.getCentralDirectoryExtraBytes();
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
    // todo make this extra field optional
    writeOutLong(Math.min(ze.getHeaderOffset(), ZIP64_MAGIC));

    writeOut(name);
    writeOut(extra);
    writeOut(commentB);
  }

  /**
   * Writes the &quot;End of central dir record&quot;.
   *
   * @throws IOException on error
   */
  private void writeCentralDirectoryEnd(long cdLength, long cdOffset) throws IOException {
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
  private byte[] getBytes(String name) throws ZipException {
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
    raf.position(writtenOnDisk);
    raf.write(ByteBuffer.wrap(myBuffer.getInternalBuffer(), 0, myBuffer.size()));
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
    ExtraFieldData extra = writeLocalFileHeader(entry);
    flushBuffer();

    FileAccessorOutputStream fileOutput = new FileAccessorOutputStream(raf);
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
    updateLocalFileHeader(entry, crc.getValue(), fileOutput.myWrittenBytes, extra);
  }

  private void prepareNextEntry(JBZipEntry entry) {
    if (entry.getMethod() == -1) {
      entry.setMethod(method);
    }

    if (entry.getTime() == -1) {
      entry.setTime(System.currentTimeMillis());
    }

    if (myFile.isZip64()) {
      // will be overwritten after offset is known
      Zip64ExtraField field = new Zip64ExtraField(new ZipUInt64(entry.getSize()),
                                                  new ZipUInt64(entry.getCompressedSize()),
                                                  new ZipUInt64(0));
      entry.addExtra(field);
    }
  }

  long getWritten() {
    return writtenOnDisk + myBuffer.size();
  }

  private static class FileAccessorOutputStream extends OutputStream {
    private final SeekableByteChannel myAccessor;
    private long myWrittenBytes;

    FileAccessorOutputStream(SeekableByteChannel file) {
      myAccessor = file;
    }

    @Override
    public void write(int b) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(1);
      buf.put((byte)b);
      buf.flip();
      myAccessor.write(buf);
      myWrittenBytes++;
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
      myAccessor.write(ByteBuffer.wrap(b, off, len));
      myWrittenBytes += len;
    }
  }

  private static class ExtraFieldData {
    private final long offset;
    private final long length;

    ExtraFieldData(long offset, long length) {
      this.offset = offset;
      this.length = length;
    }
  }
}
