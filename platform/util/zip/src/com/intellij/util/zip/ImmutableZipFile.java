// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.zip;

import com.intellij.util.io.DirectByteBufferPool;
import com.intellij.util.io.Murmur3_32Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.zip.ZipException;

public final class ImmutableZipFile implements Closeable {
  private static final int MIN_EOCD_SIZE = 22;

  private final ImmutableZipEntry[] nameMap;

  // only file entries - directories are ignored
  private final ImmutableZipEntry[] entries;

  final FileChannel fileChannel;
  final int fileSize;

  private ImmutableZipFile(ImmutableZipEntry[] nameMap,
                           ImmutableZipEntry[] entries,
                           FileChannel fileChannel,
                           int fileSize) {
    this.fileChannel = fileChannel;
    this.fileSize = fileSize;

    this.nameMap = nameMap;
    this.entries = entries;
  }

  public static @NotNull ImmutableZipFile load(@NotNull Path file) throws IOException {
    return load(file, null);
  }

  public static @NotNull ImmutableZipFile load(@NotNull Path file, @Nullable Consumer<ByteBuffer> commentConsumer) throws IOException {
    // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
    // ability to read data without setting channel position, as setting channel position will require synchronization
    FileChannel fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ));
    try {
      return populateFromCentralDirectory(fileChannel, commentConsumer);
    }
    catch (Throwable e) {
      try {
        fileChannel.close();
      }
      catch (IOException ignore) {
      }
      throw e;
    }
  }

  public ImmutableZipEntry[] getEntries() {
    return entries;
  }

  public ImmutableZipEntry[] getRawNameSet() {
    return nameMap;
  }

  @Override
  public String toString() {
    return fileChannel.toString();
  }

  /**
   * Closes the archive.
   *
   * @throws IOException if an error occurs closing the archive.
   */
  @Override
  public void close() throws IOException {
    fileChannel.close();
  }

  /**
   * Returns a named entry - or {@code null} if no entry by that name exists.
   *
   * For directories must be name without ending slash used.
   */
  public ImmutableZipEntry getEntry(String name) {
    int index = probe(name, Murmur3_32Hash.MURMUR3_32.hashString(name, 0, name.length()), nameMap);
    return index >= 0 ? nameMap[index] : null;
  }

  public ImmutableZipEntry getEntry(String name, int murmur3HashCode) {
    int index = probe(name, murmur3HashCode, nameMap);
    return index >= 0 ? nameMap[index] : null;
  }

  private static ImmutableZipFile populateFromCentralDirectory(@NotNull FileChannel fileChannel,
                                                               @Nullable Consumer<ByteBuffer> commentConsumer) throws IOException {
    // https://en.wikipedia.org/wiki/ZIP_(file_format)
    int entryCount;
    int centralDirSize = -1;
    int centralDirPosition;
    int fileSize = (int)fileChannel.size();
    ByteBuffer buffer = DirectByteBufferPool.DEFAULT_POOL.allocate(MIN_EOCD_SIZE * 2).order(ByteOrder.LITTLE_ENDIAN);
    try {
      readEndSignature(buffer, fileSize, fileChannel);
      int offset = buffer.position();
      entryCount = buffer.getShort(offset + 10) & 0xffff;
      centralDirSize = buffer.getInt(offset + 12);
      centralDirPosition = buffer.getInt(offset + 16);

      if (commentConsumer != null) {
        buffer.position(offset + 20);
        int commentLength = buffer.getShort() & 0xffff;
        if (commentLength > 0) {
          commentConsumer.accept(buffer);
          buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
      }
    }
    finally {
      if (buffer.capacity() < centralDirSize) {
        DirectByteBufferPool.DEFAULT_POOL.release(buffer);
        buffer = null;
      }
    }

    try {
      if (buffer == null) {
        buffer = DirectByteBufferPool.DEFAULT_POOL.allocate(centralDirSize).order(ByteOrder.LITTLE_ENDIAN);
      }
      else {
        buffer.rewind();
        buffer.limit(centralDirSize);
      }

      ImmutableZipEntry[] entries = new ImmutableZipEntry[entryCount];
      // ensure table is even length
      if (entryCount == 65535) {
        // it means that more than 65k entries - estimate number of entries
        entryCount = centralDirPosition / 47 /* min 46 for entry and 1 for filename */;
      }

      int entrySetLength = entryCount * 2 /* expand factor */;
      ImmutableZipEntry[] entrySet = new ImmutableZipEntry[entrySetLength];
      readFully(buffer, fileChannel, centralDirPosition);
      int fileEntryCount = readCentralDirectory(buffer, centralDirSize, entrySet, entries);
      if (entries.length != fileEntryCount) {
        ImmutableZipEntry[] resizedEntries = new ImmutableZipEntry[fileEntryCount];
        System.arraycopy(entries, 0, resizedEntries, 0, fileEntryCount);
        entries = resizedEntries;
      }
      return new ImmutableZipFile(entrySet, entries, fileChannel, fileSize);
    }
    finally {
      if (buffer != null) {
        DirectByteBufferPool.DEFAULT_POOL.release(buffer);
      }
    }
  }

  private static int readCentralDirectory(ByteBuffer buffer, int centralDirSize, ImmutableZipEntry[] entrySet, ImmutableZipEntry[] entries)
    throws EOFException {
    int offset = 0;
    int entryIndex = 0;

    // assume that file name is not greater than ~2KB
    // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
    // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decode
    byte[] tempNameBytes = new byte[4096];

    ImmutableZipEntry prevEntry = null;
    int prevEntryExpectedDataOffset = -1;
    while (offset < centralDirSize) {
      if (buffer.getInt(offset) != 33639248) {
        throw new EOFException("Expected central directory size " + centralDirSize +
                               " but only at " + offset + " no valid central directory file header signature");
      }

      int compressedSize = buffer.getInt(offset + 20);
      int uncompressedSize = buffer.getInt(offset + 24);
      int headerOffset = buffer.getInt(offset + 42);
      int method = buffer.getShort(offset + 10) & 0xffff;

      int nameLengthInBytes = buffer.getShort(offset + 28) & 0xffff;
      int extraFieldLength = buffer.getShort(offset + 30) & 0xffff;
      int commentLength = buffer.getShort(offset + 32) & 0xffff;

      if (prevEntry != null && prevEntryExpectedDataOffset == (headerOffset - prevEntry.getCompressedSize())) {
        prevEntry.setDataOffset(prevEntryExpectedDataOffset);
      }

      offset += 46;
      buffer.position(offset);

      int extraSuffixLength;
      if (buffer.get((offset + nameLengthInBytes) - 1) == '/') {
        // skip directory
        uncompressedSize = -2;
        compressedSize = -2;
        extraSuffixLength = 1;
      }
      else {
        extraSuffixLength = 0;
      }

      offset += nameLengthInBytes + extraFieldLength + commentLength;

      buffer.get(tempNameBytes, 0, nameLengthInBytes);
      String name = new String(tempNameBytes, 0, nameLengthInBytes - extraSuffixLength, StandardCharsets.UTF_8);

      int entrySetIndex = probe(name, Murmur3_32Hash.MURMUR3_32.hashBytes(tempNameBytes, 0, nameLengthInBytes - extraSuffixLength), entrySet);
      if (entrySetIndex >= 0) {
        // duplicates in xmlbeans-2.6.0.jar for example, skip it
        // throw new IllegalArgumentException("duplicate name: " + name);
        prevEntry = null;
        continue;
      }

      ImmutableZipEntry entry = new ImmutableZipEntry(name, compressedSize, uncompressedSize, headerOffset, nameLengthInBytes, method);
      prevEntry = entry;
      prevEntryExpectedDataOffset = headerOffset + 30 + nameLengthInBytes + extraFieldLength;

      entrySet[-(entrySetIndex + 1)] = entry;
      entries[entryIndex++] = entry;
    }
    return entryIndex;
  }

  static void readFully(ByteBuffer buffer, FileChannel channel, long position) throws IOException {
    int expectedLength = buffer.remaining();
    int read = 0;
    while (read < expectedLength) {
      int n = channel.read(buffer, position + read);
      if (n <= 0) {
        break;
      }
      read += n;
    }
    if (read < expectedLength) {
      throw new EOFException();
    }
  }

  private static void readEndSignature(ByteBuffer buffer, int fileSize, FileChannel channel) throws IOException {
    readFully(buffer, channel, fileSize - buffer.remaining());
    for (int offset = buffer.limit() - MIN_EOCD_SIZE; offset >= 0; offset--) {
      if (buffer.getInt(offset) == 101010256) {
        buffer.position(offset);
        return;
      }
    }

    throw new ZipException("Archive is not a ZIP archive");
  }

  // returns index at which element is present; or if absent, (-i - 1) where i is location where element should be inserted
  private static int probe(String key, int keyHash, ImmutableZipEntry[] set) {
    int index = Math.floorMod(keyHash, set.length);
    while (true) {
      ImmutableZipEntry found = set[index];
      if (found == null) {
        return -index - 1;
      }
      else if (key.equals(found.getName())) {
        return index;
      }
      else if (++index == set.length) {
        index = 0;
      }
    }
  }
}