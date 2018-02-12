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

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

/**
 * Replacement for {@code java.util.ZipFile}.
 * <p/>
 * <p>This class adds support for file name encodings other than UTF-8
 * (which is required to work on ZIP files created by native zip tools
 * and is able to skip a preamble like the one found in self
 * extracting archives.  Furthermore it returns instances of
 * {@code org.apache.tools.zip.ZipEntry} instead of
 * {@code java.util.zip.ZipEntry}.</p>
 * <p/>
 * <p>It doesn't extend {@code java.util.zip.ZipFile} as it would
 * have to reimplement all methods anyway.  Like
 * {@code java.util.ZipFile}, it uses RandomAccessFile under the
 * covers and supports compressed and uncompressed entries.</p>
 * <p/>
 * <p>The method signatures mimic the ones of
 * {@code java.util.zip.ZipFile}, with a couple of exceptions:
 * <p/>
 * <ul>
 * <li>There is no getName method.</li>
 * <li>entries has been renamed to getEntries.</li>
 * <li>getEntries and getEntry return
 * {@code org.apache.tools.zip.ZipEntry} instances.</li>
 * <li>close is allowed to throw IOException.</li>
 * </ul>
 */
public class JBZipFile {
  private static final int HASH_SIZE = 509;
  static final int SHORT = 2;
  static final int WORD = 4;
  private static final int NIBLET_MASK = 0x0f;
  private static final int BYTE_SHIFT = 8;
  private static final int POS_0 = 0;
  private static final int POS_1 = 1;
  private static final int POS_2 = 2;
  private static final int POS_3 = 3;

  /**
   * Maps ZipEntrys to Longs, recording the offsets of the local
   * file headers.
   */
  private final List<JBZipEntry> entries = new ArrayList<JBZipEntry>(HASH_SIZE);

  /**
   * Maps String to ZipEntrys, name -> actual entry.
   */
  private final Map<String, JBZipEntry> nameMap = new HashMap<String, JBZipEntry>(HASH_SIZE);

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   */
  private final Charset encoding;

  /**
   * The actual data source.
   */
  final RandomAccessFile archive;

  private JBZipOutputStream myOutputStream;
  private long currentcfdfoffset;

  /**
   * Opens the given file for reading, assuming the platform's
   * native encoding for file names.
   *
   * @param f the archive.
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(File f) throws IOException {
    this(f, CharsetToolkit.UTF8_CHARSET);
  }

  /**
   * Opens the given file for reading, assuming the platform's
   * native encoding for file names.
   *
   * @param name name of the archive.
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(String name) throws IOException {
    this(new File(name), CharsetToolkit.UTF8_CHARSET);
  }

  /**
   * Opens the given file for reading, assuming the specified
   * encoding for file names.
   *
   * @param name     name of the archive.
   * @param encoding the encoding to use for file names
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(String name, @NotNull String encoding) throws IOException {
    this(new File(name), encoding);
  }

  /**
   * Opens the given file for reading, assuming the specified
   * encoding for file names.
   *
   * @param f        the archive.
   * @param encoding the encoding to use for file names
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(File f, @NotNull String encoding) throws IOException {
    this(f, Charset.forName(encoding));
  }
  public JBZipFile(File f, @NotNull Charset encoding) throws IOException {
    this.encoding = encoding;
    archive = new RandomAccessFile(f, "rw");
    try {
      if (archive.length() > 0) {
        populateFromCentralDirectory();
      }
      else {
        getOutputStream(); // Ensure we'll write central directory when closed even if no single entry created.
      }
    }
    catch (IOException e) {
      try {
        archive.close();
      }
      catch (IOException e2) {
        // swallow, throw the original exception instead
      }
      throw e;
    }
  }

  /**
   * The encoding to use for filenames and the file comment.
   *
   * @return null if using the platform's default character encoding.
   */
  public Charset getEncoding() {
    return encoding;
  }

  /**
   * Closes the archive.
   *
   * @throws IOException if an error occurs closing the archive.
   */
  public void close() throws IOException {
    if (myOutputStream != null) {
      if (entries.isEmpty()) {
        final JBZipEntry empty = getOrCreateEntry("/empty.file.marker");
        myOutputStream.putNextEntryBytes(empty, "empty".getBytes());
      }
      
      myOutputStream.finish();
      archive.setLength(myOutputStream.getWritten());
    }
    archive.close();
  }

  /**
   * Returns all entries.
   *
   * @return all entries as {@link JBZipEntry} instances
   */
  public List<JBZipEntry> getEntries() {
    return entries;
  }

  /**
   * Returns a named entry - or {@code null} if no entry by
   * that name exists.
   *
   * @param name name of the entry.
   * @return the ZipEntry corresponding to the given name - or
   *         {@code null} if not present.
   */
  public JBZipEntry getEntry(String name) {
    return nameMap.get(name);
  }

  public JBZipEntry getOrCreateEntry(String name) {
    JBZipEntry entry = nameMap.get(name);
    if (entry != null) return entry;

    entry = new JBZipEntry(name, this);
    nameMap.put(name, entry);
    entries.add(entry);
    return entry;
  }

  private static final int CFH_LEN =
    /* version made by                 */ SHORT
                                          /* version needed to extract       */ + SHORT
                                          /* general purpose bit flag        */ + SHORT
                                          /* compression method              */ + SHORT
                                          /* last mod file time              */ + SHORT
                                          /* last mod file date              */ + SHORT
                                          /* crc-32                          */ + WORD
                                          /* compressed size                 */ + WORD
                                          /* uncompressed size               */ + WORD
                                          /* filename length                 */ + SHORT
                                          /* extra field length              */ + SHORT
                                          /* file comment length             */ + SHORT
                                          /* disk number start               */ + SHORT
                                          /* internal file attributes        */ + SHORT
                                          /* external file attributes        */ + WORD
                                          /* relative offset of local header */ + WORD;

  /**
   * Reads the central directory of the given archive and populates
   * the internal tables with ZipEntry instances.
   * <p/>
   * <p>The ZipEntrys will know all data that can be obtained from
   * the central directory alone, but not the data that requires the
   * local file header or additional data to be read.</p>
   */
  private void populateFromCentralDirectory() throws IOException {
    positionAtCentralDirectory();

    byte[] cfh = new byte[CFH_LEN];

    byte[] signatureBytes = new byte[WORD];
    archive.readFully(signatureBytes);
    long sig = ZipLong.getValue(signatureBytes);
    final long cfhSig = ZipLong.getValue(JBZipOutputStream.CFH_SIG);
    while (sig == cfhSig) {
      archive.readFully(cfh);
      int off = 0;

      int versionMadeBy = ZipShort.getValue(cfh, off);
      off += SHORT;
      final int platform = (versionMadeBy >> BYTE_SHIFT) & NIBLET_MASK;

      off += WORD; // skip version info and general purpose byte

      final int method = ZipShort.getValue(cfh, off);
      off += SHORT;

      long time = DosTime.dosToJavaTime(ZipLong.getValue(cfh, off));
      off += WORD;

      final long crc = ZipLong.getValue(cfh, off);
      off += WORD;

      final long compressedSize = ZipLong.getValue(cfh, off);
      off += WORD;

      final long uncompressedSize = ZipLong.getValue(cfh, off);
      off += WORD;

      int fileNameLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      int extraLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      int commentLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      off += SHORT; // disk number

      final int internalAttributes = ZipShort.getValue(cfh, off);
      off += SHORT;

      final long externalAttributes = ZipLong.getValue(cfh, off);
      off += WORD;

      long localHeaderOffset = ZipLong.getValue(cfh, off);

      String name = getString(readBytes(fileNameLen));
      byte[] extra = readBytes(extraLen);
      String comment = getString(readBytes(commentLen));

      JBZipEntry ze = new JBZipEntry(this);
      ze.setName(name);
      ze.setHeaderOffset(localHeaderOffset);
      ze.setPlatform(platform);
      ze.setMethod(method);
      ze.setTime(time);
      ze.setCrc(crc);
      ze.setCompressedSize(compressedSize);
      ze.setSize(uncompressedSize);
      ze.setInternalAttributes(internalAttributes);
      ze.setExternalAttributes(externalAttributes);
      ze.setExtra(extra);
      try {
        ze.setComment(comment);
      }
      catch (IllegalArgumentException e) {
        ze.setComment(comment.substring(0, 0xffff / 3));
      }

      nameMap.put(ze.getName(), ze);
      entries.add(ze);

      archive.readFully(signatureBytes);
      sig = ZipLong.getValue(signatureBytes);
    }
  }

  private byte[] readBytes(int count) throws IOException {
    if (count > 0) {
      byte[] bytes = new byte[count];
      archive.readFully(bytes);
      return bytes;
    }
    else {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

  private static final int MIN_EOCD_SIZE =
    /* end of central dir signature    */ WORD
                                          /* number of this disk             */ + SHORT
                                          /* number of the disk with the     */
                                          /* start of the central directory  */ + SHORT
                                          /* total number of entries in      */
                                          /* the central dir on this disk    */ + SHORT
                                          /* total number of entries in      */
                                          /* the central dir                 */ + SHORT
                                          /* size of the central directory   */ + WORD
                                          /* offset of start of central      */
                                          /* directory with respect to       */
                                          /* the starting disk number        */ + WORD
                                          /* zipfile comment length          */ + SHORT;

  private static final int CFD_LOCATOR_OFFSET =
    /* end of central dir signature    */ WORD
                                          /* number of this disk             */ + SHORT
                                          /* number of the disk with the     */
                                          /* start of the central directory  */ + SHORT
                                          /* total number of entries in      */
                                          /* the central dir on this disk    */ + SHORT
                                          /* total number of entries in      */
                                          /* the central dir                 */ + SHORT
                                          /* size of the central directory   */ + WORD;

  /**
   * Searches for the &quot;End of central dir record&quot;, parses
   * it and positions the stream at the first central directory
   * record.
   */
  private void positionAtCentralDirectory() throws IOException {
    boolean found = false;
    long off = archive.length() - MIN_EOCD_SIZE;
    if (off >= 0) {
      archive.seek(off);
      byte[] sig = JBZipOutputStream.EOCD_SIG;
      int curr = archive.read();
      while (curr != -1) {
        if (curr == sig[POS_0]) {
          curr = archive.read();
          if (curr == sig[POS_1]) {
            curr = archive.read();
            if (curr == sig[POS_2]) {
              curr = archive.read();
              if (curr == sig[POS_3]) {
                found = true;
                break;
              }
            }
          }
        }
        archive.seek(--off);
        curr = archive.read();
      }
    }
    if (!found) {
      throw new ZipException("archive is not a ZIP archive");
    }
    archive.seek(off + CFD_LOCATOR_OFFSET);
    byte[] cfdOffset = new byte[WORD];
    archive.readFully(cfdOffset);
    currentcfdfoffset = ZipLong.getValue(cfdOffset);
    archive.seek(currentcfdfoffset);
  }

  /**
   * Number of bytes in local file header up to the &quot;crc&quot; entry.
   */
  static final long LFH_OFFSET_FOR_CRC =
    /* local file header signature     */ WORD
                                          /* version needed to extract       */ + SHORT
                                          /* general purpose bit flag        */ + SHORT
                                          /* compression method              */ + SHORT
                                          /* last mod file time              */ + SHORT
                                          /* last mod file date              */ + SHORT;

  /**
   * Number of bytes in local file header up to the &quot;length of filename&quot; entry.
   */
  static final long LFH_OFFSET_FOR_FILENAME_LENGTH =
                                          LFH_OFFSET_FOR_CRC
                                          /* crc-32                          */ + WORD
                                          /* compressed size                 */ + WORD
                                          /* uncompressed size               */ + WORD;

  /**
   * Retrieve a String from the given bytes using the encoding set
   * for this ZipFile.
   *
   * @param bytes the byte array to transform
   * @return String obtained by using the given encoding
   */
  private String getString(byte[] bytes) {
    if (encoding == null) {
      return new String(bytes);
    }
    else {
      return new String(bytes, encoding);
    }
  }

  public void eraseEntry(JBZipEntry entry) throws IOException {
    getOutputStream(); // Ensure OutputStream created, so we'll print out central directory at the end;
    entries.remove(entry);
    nameMap.remove(entry.getName());
  }

  JBZipOutputStream getOutputStream() throws IOException {
    if (myOutputStream == null) {
      myOutputStream = new JBZipOutputStream(this, currentcfdfoffset);
    }
    return myOutputStream;
  }

  void ensureFlushed(long end) throws IOException {
    if (myOutputStream != null) myOutputStream.ensureFlushed(end);
  }
}
