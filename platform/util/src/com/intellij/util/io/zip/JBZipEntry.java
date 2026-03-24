// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Extension that adds better handling of extra fields and provides
 * access to the internal and external file attributes.
 */
@SuppressWarnings("OctalInteger")
public class JBZipEntry {
  private static final int PLATFORM_UNIX = 3;
  private static final int PLATFORM_FAT = 0;
  private static final int SHORT_MASK = 0xFFFF;
  private static final int SHORT_SHIFT = 16;

  private volatile long time = -1;     // modification time (in DOS time)
  private volatile long crc = -1;      // crc-32 of entry data
  private volatile long size = -1;     // uncompressed size of entry data
  private volatile long csize = -1;    // compressed size of entry data
  private volatile int method = -1;    // compression method
  private volatile List<JBZipExtraField> extra = new SmartList<>();   // optional extra field data for entry
  private volatile String comment;     // optional comment string for entry

  private volatile int internalAttributes = 0;
  private volatile int platform = PLATFORM_FAT;
  private volatile long externalAttributes = 0;
  private volatile String name;

  private volatile long headerOffset = -1;
  private final JBZipFile myFile;


  /**
   * Creates a new zip entry with the specified name.
   *
   * @param name the name of the entry
   */
  protected JBZipEntry(String name, JBZipFile file) {
    this.name = name;
    myFile = file;
  }

  protected JBZipEntry(JBZipFile file) {
    name = "";
    myFile = file;
  }

  /**
   * Retrieves the internal file attributes.
   *
   * @return the internal file attributes
   */
  public int getInternalAttributes() {
    return internalAttributes;
  }

  /**
   * Sets the internal file attributes.
   *
   * @param value an {@code int} value
   */
  public void setInternalAttributes(int value) {
    internalAttributes = value;
  }

  /**
   * Retrieves the external file attributes.
   *
   * @return the external file attributes
   */
  public long getExternalAttributes() {
    return externalAttributes;
  }

  /**
   * Sets the external file attributes.
   *
   * @param value an {@code long} value
   */
  public void setExternalAttributes(long value) {
    externalAttributes = value;
  }

  public long getHeaderOffset() {
    return headerOffset;
  }

  public void setHeaderOffset(long headerOffset) {
    this.headerOffset = headerOffset;
  }

  /**
   * Sets Unix permissions in a way that is understood by Info-Zip's
   * unzip command.
   *
   * @param mode an {@code int} value
   */
  public void setUnixMode(int mode) {
    setExternalAttributes(((long)(mode & SHORT_MASK) << 16)
                          // MS-DOS read-only attribute
                          | ((mode & 0200) == 0 ? 1 : 0)
                          // MS-DOS directory flag
                          | (isDirectory() ? 0x10 : 0));
    platform = PLATFORM_UNIX;
  }

  /**
   * Unix permission.
   *
   * @return the unix permissions
   */
  public int getUnixMode() {
    return (int)((getExternalAttributes() >> SHORT_SHIFT) & SHORT_MASK);
  }

  /**
   * Platform specification to put into the &quot;version made
   * by&quot; part of the central file header.
   *
   * @return 0 (MS-DOS FAT) unless {@link #setUnixMode setUnixMode}
   *         has been called, in which case 3 (Unix) will be returned.
   */
  public int getPlatform() {
    return platform;
  }

  /**
   * Set the platform (UNIX or FAT).
   *
   * @param platform an {@code int} value - 0 is FAT, 3 is UNIX
   */
  protected void setPlatform(int platform) {
    this.platform = platform;
  }

  /**
   * Sets the optional extra field data for the entry.
   * @param extra the extra field data bytes
   * @exception IllegalArgumentException if the length of the specified
   *		  extra field data is greater than 0xFFFF bytes
   * @see #getExtra()
   */
  void setExtra(@NotNull List<? extends JBZipExtraField> extra) {
      this.extra = new SmartList<>(extra);
  }

  public void addExtra(@NotNull JBZipExtraField field) {
    JBZipExtraField current = ContainerUtil.find(extra, f -> f.getHeaderId().equals(field.getHeaderId()));
    if (current != null) {
      extra.remove(current);
    }
    extra.add(field);
  }

  /**
   * Sets the modification time of the entry.
   *
   * @param time the entry modification time in number of milliseconds
   *             since the epoch
   * @see #getTime()
   */
  public void setTime(long time) {
    this.time = time;
  }

  /**
   * Returns the modification time of the entry, or -1 if not specified.
   *
   * @return the modification time of the entry, or -1 if not specified
   * @see #setTime(long)
   */
  public long getTime() {
    return time;
  }

  /**
   * Sets the uncompressed size of the entry data.
   *
   * @param size the uncompressed size in bytes
   * @throws IllegalArgumentException if the specified size is less
   *                                  than 0 or greater than 0xFFFFFFFF bytes
   * @see #getSize()
   */
  public void setSize(long size) {
    if (size < 0 || size > 0xFFFFFFFFL) {
      throw new IllegalArgumentException("invalid entry size");
    }
    this.size = size;
  }

  /**
   * Returns the uncompressed size of the entry data, or -1 if not known.
   *
   * @return the uncompressed size of the entry data, or -1 if not known
   * @see #setSize(long)
   */
  public long getSize() {
    return size;
  }

  /**
   * Get the name of the entry.
   *
   * @return the entry name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the size of the compressed entry data, or -1 if not known.
   * In the case of a stored entry, the compressed size will be the same
   * as the uncompressed size of the entry.
   *
   * @return the size of the compressed entry data, or -1 if not known
   * @see #setCompressedSize(long)
   */
  public long getCompressedSize() {
    return csize;
  }

  /**
   * Sets the size of the compressed entry data.
   *
   * @param csize the compressed size to set to
   * @see #getCompressedSize()
   */
  public void setCompressedSize(long csize) {
    this.csize = csize;
  }

  /**
   * Sets the CRC-32 checksum of the uncompressed entry data.
   *
   * @param crc the CRC-32 value
   * @throws IllegalArgumentException if the specified CRC-32 value is
   *                                  less than 0 or greater than 0xFFFFFFFF
   * @see #getCrc()
   */
  public void setCrc(long crc) {
    if (crc < 0 || crc > 0xFFFFFFFFL) {
      throw new IllegalArgumentException("invalid entry crc-32");
    }
    this.crc = crc;
  }

  /**
   * Returns the CRC-32 checksum of the uncompressed entry data, or -1 if
   * not known.
   *
   * @return the CRC-32 checksum of the uncompressed entry data, or -1 if
   *         not known
   * @see #setCrc(long)
   */
  public long getCrc() {
    return crc;
  }

  /**
   * Sets the compression method for the entry.
   *
   * @param method the compression method, either STORED or DEFLATED
   * @throws IllegalArgumentException if the specified compression
   *                                  method is invalid
   * @see #getMethod()
   */
  public void setMethod(int method) {
    if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
      throw new IllegalArgumentException("invalid compression method: " + method);
    }
    this.method = method;
  }

  /**
   * Returns the compression method of the entry, or -1 if not specified.
   *
   * @return the compression method of the entry, or -1 if not specified
   * @see #setMethod(int)
   */
  public int getMethod() {
    return method;
  }

  /**
   * Is this entry a directory?
   *
   * @return true if the entry is a directory
   */
  public boolean isDirectory() {
    return getName().endsWith("/");
  }

  /**
   * Set the name of the entry.
   *
   * @param name the name to use
   */
  protected void setName(String name) {
    this.name = name;
  }

  /**
   * Get the hashCode of the entry.
   * This uses the name as the hashcode.
   *
   * @return a hashcode.
   */
  @Override
  public int hashCode() {
    // this method has severe consequences on performance. We cannot rely
    // on the super.hashCode() method since super.getName() always return
    // the empty string in the current implementation (there's no setter)
    // so it is basically draining the performance of a hashmap lookup
    return getName().hashCode();
  }

  public void erase() throws IOException {
    myFile.eraseEntry(this);
  }

  @ApiStatus.Internal
  public InputStream getInputStream() throws IOException {
    // When reading over a remote channel (EEL/IJent), every I/O call is a gRPC round-trip.
    // The default path does 2 round-trips per entry:
    //   1) calcDataOffset() — reads 4 bytes from Local File Header to find where data starts
    //   2) BoundedInputStream.read() — reads the actual compressed data
    // The combined-read path merges both into a single read: it fetches the LFH + compressed data
    // together, then parses the data offset from the in-memory buffer.
    // Only used for readonly archives (no ensureFlushed needed) and entries <= 10 MB (to avoid
    // excessive memory allocation). Falls back to the default path if the buffer estimate was too small.
    if (myFile.isRemoteIo && myFile.myIsReadonly) {
      long compressedSize = getCompressedSize();
      if (compressedSize >= 0 && compressedSize <= 10 * 1024 * 1024) {
        InputStream result = getInputStreamCombinedRead(compressedSize);
        if (result != null) {
          return result;
        }
      }
    }
    return getInputStreamDefault();
  }

  // Original two-read path: calcDataOffset() + BoundedInputStream.
  // Used for local I/O, writable archives, large entries, and as a fallback.
  private InputStream getInputStreamDefault() throws IOException {
    myFile.ensureFlushed(getHeaderOffset() + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.WORD);
    long start = calcDataOffset();
    long size = getCompressedSize();
    myFile.ensureFlushed(start + size);
    if (myFile.getSize() < start + size) {
      throw new EOFException();
    }
    BoundedInputStream bis = new BoundedInputStream(start, size);
    switch (getMethod()) {
      case ZipEntry.STORED:
        return bis;
      case ZipEntry.DEFLATED:
        bis.addDummy();
        int bufferSize;
        if (myFile.isRemoteIo) {
          // Remote channel (e.g. IJent/EEL): each BoundedInputStream.read() is a network round-trip.
          // Size the buffer to the compressed entry size so all data is fetched in fewer calls.
          // Capped at 128 KB to limit memory; matches IJent RECOMMENDED_MAX_PACKET_SIZE.
          bufferSize = (int)Math.min(Math.max(size, 8192L), 131072L);
        }
        else {
          bufferSize = this.size <= 0 ? 8192 : (int)Math.min(this.size, 8192);
        }
        return new InflaterInputStream(bis, new Inflater(true), bufferSize);
      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  /**
   * Reads the Local File Header and compressed data in a single I/O operation,
   * eliminating the extra round-trip that {@link #calcDataOffset()} would cause over remote channels.
   * <p>
   * ZIP Local File Header layout:
   * <pre>
   *   [0..3]   signature
   *   [4..25]  fixed fields (version, flags, method, time, crc, sizes)
   *   [26..27] filename length (N)
   *   [28..29] extra field length (M)
   *   [30..30+N-1]     filename
   *   [30+N..30+N+M-1] extra field
   *   [30+N+M..]       compressed data starts here
   * </pre>
   * We don't know N and M before reading the LFH, so we estimate the header size generously
   * (UTF-8 worst-case for the name + 256 bytes padding for extra field). If the estimate was
   * too small (extra field larger than expected), we return {@code null} and the caller falls
   * back to the two-read path.
   *
   * @return an InputStream over the entry data, or {@code null} if the buffer was too small (caller should fall back)
   */
  private InputStream getInputStreamCombinedRead(long compressedSize) throws IOException {
    long headerOffset = getHeaderOffset();

    // lfhFixedEnd = offset right after the two length fields (byte 30 in LFH).
    // That's where the variable-length filename starts.
    int lfhFixedEnd = (int)JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.WORD;

    // Estimate how much to read: LFH header (fixed + variable) + compressed data.
    // name.length() * 3: UTF-8 can expand up to 3 bytes per char.
    // + 256: generous padding for the extra field (typically small, but LFH extra
    //        can differ from central directory extra).
    int nameEstimate = name.length() * 3 + 256;
    int headerEstimate = lfhFixedEnd + nameEstimate;
    int totalToRead = headerEstimate + (int)compressedSize;

    // For entries near the end of the file, our padded estimate may overshoot.
    // Cap to the actual available bytes to avoid EOFException from readFullyFromPosition.
    long available = myFile.getSize() - headerOffset;
    if (available < lfhFixedEnd) {
      return null; // not enough data even for the fixed LFH part
    }
    if (totalToRead > available) {
      totalToRead = (int)available;
    }

    // Single I/O operation: read LFH + compressed data into one buffer.
    byte[] buf = new byte[totalToRead];
    myFile.readFullyFromPosition(buf, headerOffset);

    // Parse actual filename and extra field lengths from the buffer (not from the file again).
    int actualNameLen = ZipShort.getValue(buf, (int)JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH);
    int actualExtraLen = ZipShort.getValue(buf, (int)JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.SHORT);
    int dataOffset = lfhFixedEnd + actualNameLen + actualExtraLen;

    // Verify that the buffer contains the full compressed data.
    // If not (extra field was larger than our 256-byte estimate), fall back.
    if (dataOffset + compressedSize > buf.length) {
      return null;
    }

    // Data is already in memory — wrap in ByteArrayInputStream (no further I/O needed).
    // Note: we can't reuse BoundedInputStream here because it reads from the file,
    // which would defeat the purpose of the combined read.
    ByteArrayInputStream compressedStream = new ByteArrayInputStream(buf, dataOffset, (int)compressedSize);
    switch (getMethod()) {
      case ZipEntry.STORED:
        return compressedStream;
      case ZipEntry.DEFLATED:
        // Inflater(nowrap=true) needs an extra dummy byte after the compressed data — see Inflater javadocs.
        // BoundedInputStream uses addDummy() for this; here we append it via SequenceInputStream.
        InputStream withDummy = new SequenceInputStream(compressedStream, new ByteArrayInputStream(new byte[]{0}));
        int inflaterBuf = (int)Math.min(Math.max(compressedSize, 8192L), 131072L);
        return new InflaterInputStream(withDummy, new Inflater(true), inflaterBuf);
      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  /**
   * Returns the extra field data.
   *
   * @return the extra field data
   * @see #addExtra(JBZipExtraField)
   */
  public @NotNull List<JBZipExtraField> getExtra() {
    return extra;
  }

  /**
   * Retrieves the extra data for central directory file record.
   *
   * @return the extra data for central directory file record
   */
  byte @NotNull [] getCentralDirectoryExtraBytes() throws IOException {
    try (BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream()) {
      for (JBZipExtraField field : extra) {
        stream.write(field.getHeaderId().getBytes());
        stream.write(field.getCentralDirectoryLength().getBytes());
        stream.write(field.getCentralDirectoryData());
      }
      byte[] bytes = stream.toByteArray();
      assertValidExtraFieldSize(bytes);
      return bytes;
    }
  }

  /**
   * Retrieves the extra data for the local file data.
   *
   * @return the extra data for local file header
   */
  byte @NotNull [] getLocalFileHeaderDataExtra() throws IOException {
    try (BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream()) {
      for (JBZipExtraField field : extra) {
        stream.write(field.getHeaderId().getBytes());
        stream.write(field.getLocalFileDataLength().getBytes());
        stream.write(field.getLocalFileDataData());
      }
      byte[] bytes = stream.toByteArray();
      assertValidExtraFieldSize(bytes);
      return bytes;
    }
  }

  private static void assertValidExtraFieldSize(byte @NotNull [] bytes) {
    if (bytes.length > 0xFFFF) {
      throw new IllegalArgumentException("invalid extra field length");
    }
  }

  void readExtraFromCentralDirectoryBytes(byte @NotNull [] extraBytes) throws IOException {
    UnsyncByteArrayInputStream stream = new UnsyncByteArrayInputStream(extraBytes);
    while (stream.available() > 0) {
      ZipShort headerId = new ZipShort(stream.readShortLittleEndian());
      JBZipExtraField field;
      if (headerId.equals(Zip64ExtraField.HEADER_ID)) {
        field = new Zip64ExtraField();
      }
      else {
        field = new UnrecognizedExtraField(headerId);
      }
      int length = stream.readShortLittleEndian();
      field.parseFromCentralDirectoryData(readNBytes(stream, length), 0, length);
      addExtra(field);
      if (field instanceof Zip64ExtraField) {
        Zip64ExtraField zip64ExtraField = (Zip64ExtraField)field;
        ZipUInt64 compressedSize = zip64ExtraField.getCompressedSize();
        if (compressedSize != null) {
          setCompressedSize(compressedSize.getLongValue());
        }
        ZipUInt64 size = zip64ExtraField.getSize();
        if (size != null) {
          setSize(size.getLongValue());
        }
        ZipUInt64 offset = zip64ExtraField.getHeaderOffset();
        if (offset != null) {
          setHeaderOffset(offset.getLongValue());
        }
      }
    }
  }

  /**
   * Sets the optional comment string for the entry.
   *
   * @param comment the comment string
   * @throws IllegalArgumentException if the length of the specified
   *                                  comment string is greater than 0xFFFF bytes
   * @see #getComment()
   */
  public void setComment(String comment) {
    if (comment != null && comment.length() > 0xffff / 3 && getUTF8Length(comment) > 0xffff) {
      throw new IllegalArgumentException("invalid entry comment length");
    }
    this.comment = comment;
  }

  /*
  * Returns the length of String's UTF8 encoding.
  */
  private static int getUTF8Length(String s) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch <= 0x7f) {
        count++;
      }
      else if (ch <= 0x7ff) {
        count += 2;
      }
      else {
        count += 3;
      }
    }
    return count;
  }


  /**
   * Returns the comment string for the entry, or null if none.
   *
   * @return the comment string for the entry, or null if none
   * @see #setComment(String)
   */
  public String getComment() {
    return comment;
  }

  public void setData(byte[] bytes, long timestamp) throws IOException {
    time = timestamp;
    JBZipOutputStream stream = myFile.getOutputStream();
    stream.putNextEntryBytes(this, bytes);
  }

  public void setData(byte[] bytes) throws IOException {
    setData(bytes, time);
  }

  public void setDataFromFile(File file) throws IOException {
    if (file.length() < FileUtilRt.LARGE_FOR_CONTENT_LOADING / 2) {
      //for small files it's faster to load their whole content into memory so we can write it to zip sequentially
      setData(FileUtil.loadFileBytes(file));
    }
    else {
      doSetDataFromFile(file);
    }
  }

  public void setDataFromStream(@NotNull InputStream stream) throws IOException {
    myFile.getOutputStream().putNextEntryContent(this, stream);
  }

  @SuppressWarnings("IOStreamConstructor")
  void doSetDataFromFile(File file) throws IOException {
    try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
      myFile.getOutputStream().putNextEntryContent(this, input);
      assert getSize() == file.length();
    }
  }

  public void setDataFromPath(@NotNull Path file) throws IOException {
    long size = Files.size(file);
    if (size < FileUtilRt.LARGE_FOR_CONTENT_LOADING) {
      // for small files it's faster to load their whole content into memory, so we can write it to zip sequentially
      myFile.getOutputStream().putNextEntryBytes(this, Files.readAllBytes(file));
    }
    else {
      try (InputStream input = Files.newInputStream(file)) {
        myFile.getOutputStream().putNextEntryContent(this, input);
      }
    }
  }

  public void writeDataTo(OutputStream output) throws IOException {
    if (size == -1) throw new IOException("no data");

    InputStream stream = getInputStream();
    FileUtil.copy(stream, (int)size, output);
  }

  public byte[] getData() throws IOException {
    if (size == -1) {
      throw new IOException("no data");
    }

    try (InputStream stream = getInputStream()) {
      return FileUtil.loadBytes(stream, (int)size);
    }
  }

  public long calcDataOffset() throws IOException {
    long offset = getHeaderOffset();
    byte[] b = new byte[JBZipFile.WORD];
    myFile.readFullyFromPosition(b, offset + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH);
    int fileNameLen = ZipShort.getValue(b, 0);
    int extraFieldLen = ZipShort.getValue(b, JBZipFile.SHORT);
    return offset + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.WORD + fileNameLen + extraFieldLen;
  }

  private static byte[] readNBytes(@NotNull InputStream is, int length) throws IOException {
    byte[] bytes = ArrayUtil.newByteArray(length);

    int n = 0;
    int off = 0;
    while (n < length) {
      int count = is.read(bytes, off + n, length - n);
      if (count < 0) {
        throw new EOFException();
      }
      n += count;
    }

    return bytes;
  }

  /**
   * InputStream that delegates requests to the underlying
   * RandomAccessFile, making sure that only bytes from a certain
   * range can be read.
   */
  private class BoundedInputStream extends InputStream {
    private long remaining;
    private long loc;
    private boolean addDummyByte = false;

    BoundedInputStream(long start, long remaining) {
      this.remaining = remaining;
      loc = start;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        if (addDummyByte) {
          addDummyByte = false;
          b[off] = 0;
          return 1;
        }
        return -1;
      }

      if (len <= 0) {
        return 0;
      }

      if (len > remaining) {
        len = (int)remaining;
      }

      final int ret;
      ret = myFile.readFromPosition(b, off, len, loc);

      if (ret > 0) {
        loc += ret;
        remaining -= ret;
      }
      return ret;
    }

    @Override
    public int read() throws IOException {
      if (remaining-- <= 0) {
        if (addDummyByte) {
          addDummyByte = false;
          return 0;
        }
        return -1;
      }

      SeekableByteChannel archive = myFile.myArchive;
      archive.position(loc++);
      return myFile.readByte();
    }

    /**
     * Inflater needs an extra dummy byte for nowrap - see
     * Inflater's javadocs.
     */
    void addDummy() {
      addDummyByte = true;
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
