/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Extension that adds better handling of extra fields and provides
 * access to the internal and external file attributes.
 */
@SuppressWarnings({"OctalInteger"})
public class JBZipEntry implements Cloneable {
  private static final int PLATFORM_UNIX = 3;
  private static final int PLATFORM_FAT = 0;
  private static final int SHORT_MASK = 0xFFFF;
  private static final int SHORT_SHIFT = 16;

  private long time = -1;     // modification time (in DOS time)
  private long crc = -1;      // crc-32 of entry data
  private long size = -1;     // uncompressed size of entry data
  private long csize = -1;    // compressed size of entry data
  private int method = -1;    // compression method
  private byte[] extra = ArrayUtil.EMPTY_BYTE_ARRAY;   // optional extra field data for entry
  private String comment;     // optional comment string for entry

  private int internalAttributes = 0;
  private int platform = PLATFORM_FAT;
  private long externalAttributes = 0;
  private String name = null;

  private long headerOffset = -1;
  private final JBZipFile myFile;


  /**
   * Creates a new zip entry with the specified name.
   *
   * @param name the name of the entry
   * @param file
   * @since 1.1
   */
  protected JBZipEntry(String name, JBZipFile file) {
    this.name = name;
    myFile = file;
  }

  /**
   * @param file
   * @since 1.9
   */
  protected JBZipEntry(JBZipFile file) {
    name = "";
    myFile = file;
  }

  /**
   * Retrieves the internal file attributes.
   *
   * @return the internal file attributes
   * @since 1.1
   */
  public int getInternalAttributes() {
    return internalAttributes;
  }

  /**
   * Sets the internal file attributes.
   *
   * @param value an {@code int} value
   * @since 1.1
   */
  public void setInternalAttributes(int value) {
    internalAttributes = value;
  }

  /**
   * Retrieves the external file attributes.
   *
   * @return the external file attributes
   * @since 1.1
   */
  public long getExternalAttributes() {
    return externalAttributes;
  }

  /**
   * Sets the external file attributes.
   *
   * @param value an {@code long} value
   * @since 1.1
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
   * @since Ant 1.5.2
   */
  public void setUnixMode(int mode) {
    setExternalAttributes((mode << 16)
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
   * @since Ant 1.6
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
   * @since Ant 1.5.2
   */
  public int getPlatform() {
    return platform;
  }

  /**
   * Set the platform (UNIX or FAT).
   *
   * @param platform an {@code int} value - 0 is FAT, 3 is UNIX
   * @since 1.9
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
  public void setExtra(byte[] extra) {
      if (extra != null && extra.length > 0xFFFF) {
          throw new IllegalArgumentException("invalid extra field length");
      }
      this.extra = extra;
  }

  /**
   * Retrieves the extra data for the local file data.
   *
   * @return the extra data for local file
   * @since 1.1
   */
  public byte[] getLocalFileDataExtra() {
    byte[] e = getExtra();
    return e != null ? e : new byte[0];
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
   * @since 1.9
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
      throw new IllegalArgumentException("invalid compression method");
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
   * @since 1.10
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
   * @since Ant 1.7
   */
  public int hashCode() {
    // this method has severe consequences on performance. We cannot rely
    // on the super.hashCode() method since super.getName() always return
    // the empty string in the current implemention (there's no setter)
    // so it is basically draining the performance of a hashmap lookup
    return getName().hashCode();
  }

  public void erase() throws IOException {
    myFile.eraseEntry(this);
  }

  private InputStream getInputStream() throws IOException {
    myFile.ensureFlushed(getHeaderOffset() + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.WORD);
    long start = calcDataOffset();
    long size = getCompressedSize();
    myFile.ensureFlushed(start + size);
    if (myFile.archive.length() < start + size) {
      throw new EOFException();
    }
    BoundedInputStream bis = new BoundedInputStream(start, size);
    switch (getMethod()) {
      case ZipEntry.STORED:
        return bis;
      case ZipEntry.DEFLATED:
        bis.addDummy();
        return new InflaterInputStream(bis, new Inflater(true));
      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  /**
   * Returns the extra field data for the entry, or null if none.
   *
   * @return the extra field data for the entry, or null if none
   * @see #setExtra(byte[])
   */
  public byte[] getExtra() {
    return extra;
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
      //for small files its faster to load their whole content into memory so we can write it to zip sequentially
      setData(FileUtil.loadFileBytes(file));
    }
    else {
      doSetDataFromFile(file);
    }
  }

  void doSetDataFromFile(File file) throws IOException {
    InputStream input = new BufferedInputStream(new FileInputStream(file));
    try {
      myFile.getOutputStream().putNextEntryContent(this, file.length(), input);
    }
    finally {
      input.close();
    }
  }

  public void writeDataTo(OutputStream output) throws IOException {
    if (size == -1) throw new IOException("no data");

    InputStream stream = getInputStream();
    FileUtil.copy(stream, (int)size, output);
  }

  public byte[] getData() throws IOException {
    if (size == -1) throw new IOException("no data");

    final InputStream stream = getInputStream();
    try {
      return FileUtil.loadBytes(stream, (int)size);
    }
    finally {
      stream.close();
    }
  }

  private long calcDataOffset() throws IOException {
    long offset = getHeaderOffset();
    myFile.archive.seek(offset + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH);
    byte[] b = new byte[JBZipFile.WORD];
    myFile.archive.readFully(b);
    int fileNameLen = ZipShort.getValue(b, 0);
    int extraFieldLen = ZipShort.getValue(b, JBZipFile.SHORT);
    return offset + JBZipFile.LFH_OFFSET_FOR_FILENAME_LENGTH + JBZipFile.WORD + fileNameLen + extraFieldLen;
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
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
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
      RandomAccessFile archive = myFile.archive;
      archive.seek(loc);
      ret = archive.read(b, off, len);

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

      RandomAccessFile archive = myFile.archive;
      archive.seek(loc++);
      return archive.read();
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
