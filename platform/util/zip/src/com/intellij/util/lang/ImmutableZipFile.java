// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.io.Murmur3_32Hash;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

@ApiStatus.Internal
public final class ImmutableZipFile implements Closeable {
  private static final int MIN_EOCD_SIZE = 22;

  private final ImmutableZipEntry[] nameMap;

  // only file entries - directories are ignored
  private final ImmutableZipEntry[] entries;

  ByteBuffer mappedBuffer;
  final int fileSize;

  private ImmutableZipFile(ImmutableZipEntry[] nameMap,
                           ImmutableZipEntry[] entries,
                           ByteBuffer mappedBuffer,
                           int fileSize) {
    this.mappedBuffer = mappedBuffer;
    this.fileSize = fileSize;

    this.nameMap = nameMap;
    this.entries = entries;
  }

  public static @NotNull ImmutableZipFile load(@NotNull Path file) throws IOException {
    // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
    // ability to read data without setting channel position, as setting channel position will require synchronization
    int fileSize;
    ByteBuffer mappedBuffer;
    try (FileChannel fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ))) {
      fileSize = (int)fileChannel.size();
      try {
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
      }
      catch (UnsupportedOperationException e) {
        // in memory fs
        ByteBuffer buffer = ByteBuffer.allocate(fileSize);
        while (buffer.hasRemaining()) {
          fileChannel.read(buffer);
        }
        buffer.rewind();
        mappedBuffer = buffer;
      }

      mappedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    try {
      return populateFromCentralDirectory(mappedBuffer, fileSize);
    }
    catch (IOException e) {
      throw new IOException(file.toString(), e);
    }
  }

  public ImmutableZipEntry[] getEntries() {
    return entries;
  }

  public ImmutableZipEntry[] getRawNameSet() {
    return nameMap;
  }

  /**
   * Closes the archive.
   *
   * @throws IOException if an error occurs closing the archive.
   */
  @Override
  public void close() throws IOException {
    ByteBuffer buffer = mappedBuffer;
    if (buffer != null) {
      mappedBuffer = null;
      // we need to unmap buffer immediately without waiting until GC does this job; otherwise further modifications of the created file
      // will fail with AccessDeniedException
      unmapBuffer(buffer);
    }
  }

  /**
   * Returns a named entry, or {@code null} if no entry by that name exists. The name should not contain trailing slashes.
   */
  public @Nullable ImmutableZipEntry getEntry(String name) {
    int index = probe(name, Murmur3_32Hash.MURMUR3_32.hashString(name, 0, name.length()), nameMap);
    return index >= 0 ? nameMap[index] : null;
  }

  public @Nullable ImmutableZipEntry getEntry(String name, int murmur3HashCode) {
    int index = probe(name, murmur3HashCode, nameMap);
    return index >= 0 ? nameMap[index] : null;
  }

  private static @NotNull ImmutableZipFile populateFromCentralDirectory(@NotNull ByteBuffer buffer, int fileSize) throws IOException {
    // https://en.wikipedia.org/wiki/ZIP_(file_format)
    int offset =  readEndSignature(buffer, fileSize);
    int entryCount = buffer.getShort(offset + 10) & 0xffff;
    int centralDirSize = buffer.getInt(offset + 12);
    int centralDirPosition = buffer.getInt(offset + 16);

    int commentSize = buffer.getShort(offset + 20);
    if (commentSize == 9) {
      int commentVersion = buffer.get(offset + 22);
      if (commentVersion == 1) {
        int pos = buffer.position();

        entryCount = buffer.getInt(offset + 23);
        buffer.position(buffer.getInt(offset + 27));
        IntBuffer intBuffer = buffer.asIntBuffer();

        int[] sizes = new int[entryCount];
        int[] dataOffsets = new int[entryCount];
        int[] indexes = new int[entryCount];
        intBuffer.get(sizes);
        intBuffer.get(dataOffsets);
        intBuffer.get(indexes);

        buffer.position(pos);

        ImmutableZipEntry[] entries = new ImmutableZipEntry[entryCount];

        int entrySetLength = entryCount * 2 /* expand factor */;
        ImmutableZipEntry[] entrySet = new ImmutableZipEntry[entrySetLength];

        //long start = System.currentTimeMillis();
        readCentralDirectoryUsingExtraMetadata(buffer, centralDirPosition, centralDirSize, entrySet, entries, sizes, dataOffsets, indexes);
        //System.out.print("optimized read took " + (System.currentTimeMillis() - start) + " ms");
        buffer.clear();
        return new ImmutableZipFile(entrySet, entries, buffer, fileSize);
      }
    }

    // ensure table is even length
    if (entryCount == 65535) {
      // it means that more than 65k entries - estimate number of entries
      entryCount = centralDirPosition / 47 /* min 46 for entry and 1 for filename */;
    }
    ImmutableZipEntry[] entries = new ImmutableZipEntry[entryCount];

    int entrySetLength = entryCount * 2 /* expand factor */;
    ImmutableZipEntry[] entrySet = new ImmutableZipEntry[entrySetLength];

    int fileEntryCount = readCentralDirectory(buffer, centralDirPosition, centralDirSize, entrySet, entries);
    if (entries.length != fileEntryCount) {
      ImmutableZipEntry[] resizedEntries = new ImmutableZipEntry[fileEntryCount];
      System.arraycopy(entries, 0, resizedEntries, 0, fileEntryCount);
      entries = resizedEntries;
    }

    buffer.clear();
    return new ImmutableZipFile(entrySet, entries, buffer, fileSize);
  }

  private static int readCentralDirectory(ByteBuffer buffer,
                                          int centralDirPosition,
                                          int centralDirSize,
                                          ImmutableZipEntry[] entrySet,
                                          ImmutableZipEntry[] entries)
    throws EOFException {
    int offset = centralDirPosition;
    int entryIndex = 0;

    // assume that file name is not greater than ~2 KiB
    // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
    // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decode
    byte[] tempNameBytes = new byte[4096];

    ImmutableZipEntry prevEntry = null;
    int prevEntryExpectedDataOffset = -1;
    int endOffset = centralDirPosition + centralDirSize;
    while (offset < endOffset) {
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

      if (prevEntry != null && prevEntryExpectedDataOffset == (headerOffset - prevEntry.compressedSize)) {
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

  @SuppressWarnings("DuplicatedCode")
  private static void readCentralDirectoryUsingExtraMetadata(ByteBuffer buffer,
                                                             int centralDirPosition,
                                                             int centralDirSize,
                                                             ImmutableZipEntry[] entrySet,
                                                             ImmutableZipEntry[] entries,
                                                             int[] sizes,
                                                             int[] dataOffsets,
                                                             int[] indexes)
    throws EOFException {
    int offset = centralDirPosition;
    int entryIndex = 0;

    // assume that file name is not greater than ~2 KiB
    // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
    // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decode
    byte[] tempNameBytes = new byte[4096];

    int endOffset = centralDirPosition + centralDirSize;
    while (offset < endOffset) {
      if (buffer.getInt(offset) != 33639248) {
        throw new EOFException("Expected central directory size " + centralDirSize +
                               " but only at " + offset + " no valid central directory file header signature");
      }

      int size = sizes[entryIndex];
      int nameLengthInBytes = buffer.getShort(offset + 28) & 0xffff;

      offset += 46;
      buffer.position(offset);

      int extraSuffixLength;
      if (buffer.get((offset + nameLengthInBytes) - 1) == '/') {
        size = -2;
        extraSuffixLength = 1;
      }
      else {
        extraSuffixLength = 0;
      }

      offset += nameLengthInBytes;

      buffer.get(tempNameBytes, 0, nameLengthInBytes);
      String name = new String(tempNameBytes, 0, nameLengthInBytes - extraSuffixLength, StandardCharsets.UTF_8);
      int entrySetIndex = indexes[entryIndex];
      // headerOffset is required only to compute data offset, but dataOffset is already known
      ImmutableZipEntry entry = new ImmutableZipEntry(name, size, size, -1, nameLengthInBytes, ZipEntry.STORED);
      entry.setDataOffset(dataOffsets[entryIndex]);
      entrySet[entrySetIndex] = entry;
      entries[entryIndex++] = entry;
    }
  }

  private static int readEndSignature(@NotNull ByteBuffer buffer, int fileSize) throws IOException {
    for (int offset = fileSize - MIN_EOCD_SIZE; offset >= 0; offset--) {
      if (buffer.getInt(offset) == 101010256) {
        return offset;
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
      else if (key.equals(found.name)) {
        return index;
      }
      else if (++index == set.length) {
        index = 0;
      }
    }
  }

  /**
   * This method repeats logic from {@link com.intellij.util.io.ByteBufferUtil#cleanBuffer} which isn't accessible from this module
   */
  private static void unmapBuffer(@NotNull ByteBuffer buffer) throws IOException {
    if (!buffer.isDirect()) {
      return;
    }

    try {
      Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      Object unsafe = unsafeField.get(null);
      MethodType type = MethodType.methodType(void.class, ByteBuffer.class);
      MethodHandle handle = MethodHandles.lookup().findVirtual(unsafe.getClass(), "invokeCleaner", type);
      handle.invoke(unsafe, buffer);
    }
    catch (Throwable t) {
      throw new IOException(t);
    }
  }
}
