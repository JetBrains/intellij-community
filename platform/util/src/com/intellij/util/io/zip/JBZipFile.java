// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.zip;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;

import static java.nio.file.StandardOpenOption.*;

/**
 * <h3>Overview</h3>
 *
 * <p>A replacement for {@code java.util.ZipFile}.</p>
 *
 * <p>This class adds support for file name encodings other than UTF-8 (which is required to work on ZIP files created by native tools),
 * and is able to skip a preamble (like the one found in self-extracting archives).</p>
 *
 * <p>As a pure-Java implementation, the class is noticeably slower (up to 2x, depending on a configuration).
 * On a positive side, the class doesn't crash a JVM when an archive is overwritten externally while open.</p>
 *
 * <h3>IntelliJ-specific changes</h3>
 * We added synchronization to this class, so now reading a single {@code JBZipFile} from several threads is safe.
 * <p>
 * {@link JBZipFile} is adapted for the scenario where the archive is physically located on a remote file system.
 * In particular, during the initialization it reads the entire central directory in one request to IO.
 * In general, this class tries to reduce the number of IO calls, hence avoiding communication over a potentially expensive channel
 * to the remote file system.
 *
 * <h3>Implementation notes</h3>
 *
 * <p>It doesn't extend {@code java.util.zip.ZipFile} as it would have to re-implement all methods anyway. Unike
 * {@link java.util.zip.ZipFile}, it uses {@link SeekableByteChannel} under the hood, and supports compressed and uncompressed entries.</p>
 *
 * <p>The method signatures mimic the ones of {@link java.util.zip.ZipFile}, with a couple of exceptions:
 * <ul>
 * <li>there is no {@code getName()} method</li>
 * <li>{@code entries()} method is renamed to {@link #getEntries()}</li>
 * <li>{@link #getEntries()} and {@link #getEntry(String)} methods return {@link JBZipEntry} instances instead of {@link java.util.zip.ZipEntry}</li>
 * <li>{@link #close()} may throw {@link IOException}</li>
 * </ul>
 * </p>
 */
public class JBZipFile implements Closeable {
  static final int SHORT = 2;
  static final int WORD = 4;
  static final int DWORD = 8;

  private static final int HASH_SIZE = 509;
  private static final int NIBLET_MASK = 0x0f;
  private static final int BYTE_SHIFT = 8;
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  /**
   * A list of entries in the file.
   */
  private final List<JBZipEntry> entries = new ArrayList<>(HASH_SIZE);

  /**
   * A map of entry names.
   */
  private final Map<String, JBZipEntry> nameMap = new ConcurrentHashMap<>(HASH_SIZE);

  /**
   * The encoding to use for filenames and the file comment
   * (see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">supported Encodings</a>).
   * Defaults to the platform encoding.
   */
  private final Charset myEncoding;

  /**
   * The actual data source.
   */
  final SeekableByteChannel myArchive;

  private boolean myIsZip64;

  private JBZipOutputStream myOutputStream;
  private long currentCfdOffset;
  private final boolean myIsReadonly;


  /**
   * Opens the given file for reading, assuming the platform's
   * native encoding for file names.
   *
   * @param f the archive.
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(File f) throws IOException {
    this(f, DEFAULT_CHARSET);
  }

  /**
   * Opens the given file for reading, assuming the platform's
   * native encoding for file names.
   *
   * @param name name of the archive.
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(String name) throws IOException {
    this(new File(name), DEFAULT_CHARSET);
  }

  /**
   * Opens the given file for reading, assuming the platform's
   * native encoding for file names.
   *
   * @param f        file of the archive.
   * @param readonly true to open file as readonly
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(@NotNull File f, boolean readonly) throws IOException {
    this(f, DEFAULT_CHARSET, readonly);
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

  /**
   * Opens the given file for reading, assuming the specified
   * encoding for file names.
   *
   * @param f        the archive.
   * @param encoding the encoding to use for file names
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(File f, @NotNull Charset encoding) throws IOException {
    this(f, encoding, false);
  }

  boolean isZip64() {
    return myIsZip64;
  }

  /**
   * Opens the given file for reading, assuming the specified
   * encoding for file names.
   *
   * @param f        the archive.
   * @param encoding the encoding to use for file names
   * @param readonly true to open file as readonly
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(@NotNull File f, @NotNull Charset encoding, boolean readonly) throws IOException {
    this(f, encoding, readonly, ThreeState.NO);
  }

  public JBZipFile(@NotNull File f, @NotNull Charset encoding, boolean readonly, @NotNull ThreeState isZip64) throws IOException {
    this(getFileChannel(f, readonly), encoding, readonly, isZip64);
  }

  /**
   * Interprets a given channel as a zip file.
   *
   * @param channel  the channel.
   * @param encoding the encoding to use for file names
   * @param readonly true to open file as readonly
   * @throws IOException if an error occurs while reading the file.
   */
  public JBZipFile(@NotNull SeekableByteChannel channel, @NotNull Charset encoding, boolean readonly, @NotNull ThreeState isZip64) throws IOException {
    myEncoding = encoding;
    myIsReadonly = readonly;
    myArchive = channel;

    try {
      if (myArchive.size() > 0) {
        populateFromCentralDirectory(isZip64);
      }
      else {
        myIsZip64 = isZip64 == ThreeState.YES;
        getOutputStream(); // Ensure we'll write central directory when closed even if no single entry created.
      }
    }
    catch (Throwable e) {
      try {
        myArchive.close();
      }
      catch (IOException e2) {
        e.addSuppressed(e2);
      }
      throw e;
    }
  }

  private static FileChannel getFileChannel(final File file, boolean isReadonly) throws IOException {
    Path path = Paths.get(file.getPath());
    return path.getFileSystem().provider().newFileChannel(path, isReadonly ? EnumSet.of(READ) : EnumSet.of(READ, WRITE, CREATE));
  }

  @Override
  public String toString() {
    return "JBZipFile{readonly=" + myIsReadonly + '}';
  }

  /**
   * The encoding to use for filenames and the file comment.
   *
   * @return null if using the platform's default character encoding.
   */
  public Charset getEncoding() {
    return myEncoding;
  }

  /**
   * Closes the archive.
   *
   * @throws IOException if an error occurs closing the archive.
   */
  @Override
  public void close() throws IOException {
    if (myOutputStream != null) {
      if (entries.isEmpty()) {
        JBZipEntry empty = getOrCreateEntry("/empty.file.marker");
        myOutputStream.putNextEntryBytes(empty, "empty".getBytes(StandardCharsets.US_ASCII));
      }

      myOutputStream.finish();
      myArchive.truncate(myOutputStream.getWritten());
    }
    myArchive.close();
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
   * <p>The ZipEntries will know all data that can be obtained from
   * the central directory alone, but not the data that requires the
   * local file header or additional data to be read.</p>
   */
  private void populateFromCentralDirectory(@NotNull ThreeState isZip64) throws IOException {
    positionAtCentralDirectory(isZip64);

    /*
      Apache ZipFile reads central directory in very small chunks -- by 4 and 40 bytes.
      This is unacceptable in situations with non-local IO, where there is no caching, and each call takes several milliseconds to finish.
      To mitigate it, we try to read and cache the entire central directory, so that the parsing of the central directory happens in-memory.

      However, the directory is anyway stored within the IDE after that, so we are willing to accept this temporary increase in memory,
      instead gaining a performance boost.
     */
    ByteBuffer centralDirectoryCached = ByteBuffer.allocate(
      Math.min((int)(myArchive.size() - myArchive.position()),
               // Sometimes the size of the central directory may be significant -- up to 3 megabytes.
               64 * 1024) // seems enough
    );
    // we must fill the buffer before the loop starts
    centralDirectoryCached.position(centralDirectoryCached.limit());


    byte[] cfh = new byte[CFH_LEN];

    byte[] signatureBytes = new byte[WORD];
    readCachedCentralDirectory(centralDirectoryCached, signatureBytes);
    long sig = ZipLong.getValue(signatureBytes);
    long cfhSig = ZipLong.getValue(JBZipOutputStream.CFH_SIG);
    while (sig == cfhSig) {
      readCachedCentralDirectory(centralDirectoryCached, cfh);
      int off = 0;

      int versionMadeBy = ZipShort.getValue(cfh, off);
      off += SHORT;
      int platform = (versionMadeBy >> BYTE_SHIFT) & NIBLET_MASK;

      off += WORD; // skip version info and general purpose byte

      int method = ZipShort.getValue(cfh, off);
      off += SHORT;

      long time = DosTime.dosToJavaTime(ZipLong.getValue(cfh, off));
      off += WORD;

      long crc = ZipLong.getValue(cfh, off);
      off += WORD;

      long compressedSize = ZipLong.getValue(cfh, off);
      off += WORD;

      long uncompressedSize = ZipLong.getValue(cfh, off);
      off += WORD;

      int fileNameLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      int extraLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      int commentLen = ZipShort.getValue(cfh, off);
      off += SHORT;

      off += SHORT; // disk number

      int internalAttributes = ZipShort.getValue(cfh, off);
      off += SHORT;

      long externalAttributes = ZipLong.getValue(cfh, off);
      off += WORD;

      long localHeaderOffset = ZipLong.getValue(cfh, off);

      String name = getString(readBytesFromBuf(centralDirectoryCached, fileNameLen));
      byte[] extra = readBytesFromBuf(centralDirectoryCached, extraLen);
      String comment = getString(readBytesFromBuf(centralDirectoryCached, commentLen));

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
      ze.readExtraFromCentralDirectoryBytes(extra);
      try {
        ze.setComment(comment);
      }
      catch (IllegalArgumentException e) {
        ze.setComment(comment.substring(0, 0xffff / 3));
      }

      nameMap.put(ze.getName(), ze);
      entries.add(ze);

      readCachedCentralDirectory(centralDirectoryCached, signatureBytes);
      sig = ZipLong.getValue(signatureBytes);
    }
  }


  private void readCachedCentralDirectory(ByteBuffer cache, byte[] target) throws IOException {
    if (cache.remaining() < target.length) {
      cache.compact();
      while (cache.hasRemaining()) {
        int rd = myArchive.read(cache);
        if (rd == -1) {
          if (cache.position() < target.length) {
            throw new EOFException("unexpected EOF");
          }
          else {
            break;
          }
        }
      }
      cache.flip();
    }
    cache.get(target);
  }

  private byte[] readBytesFromBuf(ByteBuffer buffer, int howMany) throws IOException {
    byte[] res = new byte[howMany];
    readCachedCentralDirectory(buffer, res);
    return res;
  }

  void readFully(byte[] b) throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(b);
    while (buffer.hasRemaining()) {
      if (myArchive.read(buffer) < 0) {
        throw new EOFException("unexpected EOF");
      }
    }
  }

  // tries to read content at a specific position atomically.
  // the position of the channel after this operation completes is undefined
  void readFullyFromPosition(byte[] b, long position) throws IOException {
    if (myArchive instanceof FileChannel) {
      ByteBuffer buffer = ByteBuffer.wrap(b);
      int totalRead = 0;
      while (totalRead < b.length) {
        int currentlyRead = ((FileChannel)myArchive).read(buffer, position + totalRead);
        if (currentlyRead == 0) {
          throw new EOFException("unexpected EOF");
        }
        totalRead += currentlyRead;
      }
    }
    else {
      synchronized (myArchive) {
        myArchive.position(position);
        readFully(b);
      }
    }
  }

  // tries to read content at a specific position atomically.
  // the position of the channel after this operation completes is undefined
  int readFromPosition(byte[] b, int offset, int length, long position) throws IOException {
    ByteBuffer buf = ByteBuffer.wrap(b, offset, length);
    if (myArchive instanceof FileChannel) {
      return ((FileChannel)myArchive).read(buf, position);
    }
    else {
      synchronized (myArchive) {
        myArchive.position(position);
        return myArchive.read(buf);
      }
    }
  }

  int readByte() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(1);
    if (myArchive.read(buffer) < 0) {
      return -1;
    }
    else {
      buffer.flip();
      return buffer.get(0) & 0xff;
    }
  }

  private byte[] readBytes(int count) throws IOException {
    if (count > 0) {
      byte[] bytes = new byte[count];
      readFully(bytes);
      return bytes;
    }
    else {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
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

  private static final int ZIP64_EOCDL_LENGTH =
    /* zip64 end of central dir locator sig */ WORD
    /* number of the disk with the start    */
    /* start of the zip64 end of            */
    /* central directory                    */ + WORD
    /* relative offset of the zip64         */
    /* end of central directory record      */ + DWORD
    /* total number of disks                */ + WORD;

  private static final int ZIP64_EOCDL_LOCATOR_OFFSET =
    /* zip64 end of central dir locator sig */ WORD
    /* number of the disk with the start    */
    /* start of the zip64 end of            */
    /* central directory                    */ + WORD;

  private static final int ZIP64_EOCD_CFD_LOCATOR_OFFSET =
    /* zip64 end of central dir        */
    /* signature                       */ WORD
    /* size of zip64 end of central    */
    /* directory record                */ + DWORD
    /* version made by                 */ + SHORT
    /* version needed to extract       */ + SHORT
    /* number of this disk             */ + WORD
    /* number of the disk with the     */
    /* start of the central directory  */ + WORD
    /* total number of entries in the  */
    /* central directory on this disk  */ + DWORD
    /* total number of entries in the  */
    /* central directory               */ + DWORD
    /* size of the central directory   */ + DWORD;

  /**
   * Searches for the &quot;End of central dir record&quot;, parses
   * it and positions the stream at the first central directory
   * record.
   */
  private void positionAtCentralDirectory(@NotNull ThreeState isZip64) throws IOException {
    boolean found = false;
    long off = myArchive.size() - MIN_EOCD_SIZE;
    if (off >= 0) {
      myArchive.position(off);
      byte[] sig = JBZipOutputStream.EOCD_SIG;
      int curr = readByte();
      while (curr != -1) {
        if (curr == sig[0]) {
          curr = readByte();
          if (curr == sig[1]) {
            curr = readByte();
            if (curr == sig[2]) {
              curr = readByte();
              if (curr == sig[3]) {
                found = true;
                break;
              }
            }
          }
        }
        myArchive.position(--off);
        curr = readByte();
      }
    }
    if (!found) {
      throw new ZipException("archive is not a ZIP archive");
    }

    boolean searchForZip64EOCD = myArchive.position() > ZIP64_EOCDL_LENGTH;
    if (searchForZip64EOCD) {
      myArchive.position(myArchive.position() - ZIP64_EOCDL_LENGTH - WORD);
      myIsZip64 = Arrays.equals(readBytes(WORD), JBZipOutputStream.ZIP64_EOCD_LOC_SIG);
    }

    if (myIsZip64) {
      if (isZip64.equals(ThreeState.NO)) {
        throw new IOException("Non ZIP64 archive was requested but it is a ZIP64 archive");
      }

      myArchive.position(myArchive.position() + ZIP64_EOCDL_LOCATOR_OFFSET - WORD);
      myArchive.position(ZipUInt64.getLongValue(readBytes(DWORD)));
      if (!Arrays.equals(readBytes(WORD), JBZipOutputStream.ZIP64_EOCD_SIG)) {
        throw new IOException("archive is not a ZIP64 archive");
      }

      myArchive.position(myArchive.position() + ZIP64_EOCD_CFD_LOCATOR_OFFSET
                          - WORD /* signature has already been read */);
      long value = ZipUInt64.getLongValue(readBytes(DWORD));
      currentCfdOffset = value;
      myArchive.position(value);
    }
    else {
      if (isZip64.equals(ThreeState.YES)) {
        throw new IOException("ZIP64 archive was requested but it is not a ZIP64 archive");
      }

      myArchive.position(off + CFD_LOCATOR_OFFSET);
      byte[] cfdOffset = new byte[WORD];
      readFully(cfdOffset);
      currentCfdOffset = ZipLong.getValue(cfdOffset);
      myArchive.position(currentCfdOffset);
    }
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
    if (myEncoding == null) {
      return new String(bytes, Charset.defaultCharset());
    }
    else {
      return new String(bytes, myEncoding);
    }
  }

  /**
   * Removes entry from the directory list.<br/>
   * NB: it will NOT remove entry content from the stream, please call {@link JBZipFile#gc()} manually when you've finished modifying the
   * archive.<br/>
   * {@link JBZipFile#gc()} is not called on {@link JBZipFile#close()} due to potential performance impact of removing small entry from a
   * big archive.
   */
  public void eraseEntry(JBZipEntry entry) throws IOException {
    getOutputStream(); // Ensure OutputStream created, so we'll print out central directory at the end;
    entries.remove(entry);
    nameMap.remove(entry.getName());
  }

  public void gc() throws IOException {
    if (myOutputStream != null) {
      myOutputStream = null;

      final Map<JBZipEntry, byte[]> existingEntries = new LinkedHashMap<>();
      for (JBZipEntry entry : entries) {
        existingEntries.put(entry, entry.getData());
      }

      currentCfdOffset = 0;
      nameMap.clear();
      entries.clear();
      for (Map.Entry<JBZipEntry, byte[]> entry : existingEntries.entrySet()) {
        JBZipEntry zipEntry = getOrCreateEntry(entry.getKey().getName());
        zipEntry.setComment(entry.getKey().getComment());
        zipEntry.setExtra(ContainerUtil.filter(entry.getKey().getExtra(), f -> !(f instanceof Zip64ExtraField)));
        zipEntry.setMethod(entry.getKey().getMethod());
        zipEntry.setTime(entry.getKey().getTime());
        zipEntry.setData(entry.getValue());
      }
    }
  }

  JBZipOutputStream getOutputStream() throws IOException {
    if (myIsReadonly) throw new IOException("Archive " + this + " is an empty file");

    if (myOutputStream == null) {
      myOutputStream = new JBZipOutputStream(this, currentCfdOffset);
    }
    return myOutputStream;
  }

  void ensureFlushed(long end) throws IOException {
    if (myOutputStream != null) myOutputStream.ensureFlushed(end);
  }
}
