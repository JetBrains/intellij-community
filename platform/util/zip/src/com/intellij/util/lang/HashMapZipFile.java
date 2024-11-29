// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.intellij.util.lang.ImmutableZipFile.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE;

@ApiStatus.Internal
public final class HashMapZipFile implements ZipFile {
  private final ImmutableZipEntry[] nameMap;

  // only file entries - directories are ignored
  private final ImmutableZipEntry[] entries;

  ByteBuffer mappedBuffer;
  final int fileSize;

  private HashMapZipFile(ImmutableZipEntry[] nameMap,
                         ImmutableZipEntry[] entries,
                         ByteBuffer mappedBuffer,
                         int fileSize) {
    this.mappedBuffer = mappedBuffer;
    this.fileSize = fileSize;

    this.nameMap = nameMap;
    this.entries = entries;
  }

  public static @NotNull HashMapZipFile load(@NotNull Path file) throws IOException {
    return (HashMapZipFile)ImmutableZipFile.load(file, true);
  }

  public static @Nullable HashMapZipFile loadIfNotEmpty(@NotNull Path file) throws IOException {
    @SuppressWarnings("resource")
    ZipFile result = ImmutableZipFile.load(file, true);
    return result instanceof EmptyZipFile ? null : (HashMapZipFile)result;
  }

  @NotNull
  static HashMapZipFile createHashMapZipFile(@NotNull ByteBuffer buffer,
                                             int fileSize,
                                             int entryCount,
                                             int centralDirSize,
                                             int centralDirPosition) throws EOFException {
    // ensure the table is even length
    if (entryCount == 65535) {
      // it means that more than 65k entries - estimate the number of entries
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
    return new HashMapZipFile(entrySet, entries, buffer, fileSize);
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> nameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    int minNameLength = dir.length() + 2;
    for (ImmutableZipEntry entry : entries) {
      String name = entry.getName();
      if (name.length() >= minNameLength && name.charAt(dir.length()) == '/' && name.startsWith(dir) && nameFilter.test(name)) {
        try (InputStream stream = entry.getInputStream(this)) {
          consumer.accept(name, stream);
        }
      }
    }
  }

  @Override
  public @Nullable InputStream getInputStream(@NotNull String path) throws IOException {
    ImmutableZipEntry entry = getRawEntry(path.charAt(0) == '/' ? path.substring(1) : path);
    return entry == null ? null : entry.getInputStream(this);
  }

  @Override
  public byte @Nullable [] getData(@NotNull String path) throws IOException {
    ImmutableZipEntry entry = getRawEntry(path.charAt(0) == '/' ? path.substring(1) : path);
    return entry == null ? null : entry.getData(this);
  }

  @Override
  public ByteBuffer getByteBuffer(@NotNull String path) throws IOException {
    ImmutableZipEntry entry = getRawEntry(path.charAt(0) == '/' ? path.substring(1) : path);
    return entry == null ? null : entry.getByteBuffer(this, null);
  }

  /**
   * Returns a named entry, or {@code null} if no entry by that name exists. The name should not contain trailing slashes.
   */
  @Override
  public @Nullable ZipResource getResource(String name) {
    ImmutableZipEntry entry = getRawEntry(name);
    if (entry == null) {
      return null;
    }

    return new ZipResource() {
      @Override
      public @NotNull String getPath() {
        return entry.name;
      }

      @Override
      public int getUncompressedSize() {
        return entry.uncompressedSize;
      }

      @Override
      public @NotNull ByteBuffer getByteBuffer() throws IOException {
        return entry.getByteBuffer(HashMapZipFile.this, null);
      }

      @Override
      public byte @NotNull [] getData() throws IOException {
        return entry.getData(HashMapZipFile.this);
      }

      @Override
      public @NotNull InputStream getInputStream() throws IOException {
        return entry.getInputStream(HashMapZipFile.this);
      }
    };
  }

  public @Nullable ImmutableZipEntry getRawEntry(String name) {
    int index = probe(name, Xxh3.hash(name), nameMap);
    return index < 0 ? null : nameMap[index];
  }

  public ImmutableZipEntry[] getEntries() {
    return entries;
  }

  public ImmutableZipEntry[] getRawNameSet() {
    return nameMap;
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
    // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decoding
    byte[] tempNameBytes = new byte[4096];

    ImmutableZipEntry prevEntry = null;
    int prevEntryExpectedDataOffset = -1;
    int endOffset = centralDirPosition + centralDirSize;
    while (offset < endOffset) {
      if (buffer.getInt(offset) != CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
        throw new EOFException("No valid central directory file header signature present (expectedCentralDirectorySize=" + centralDirSize +
                               ", expectedCentralDirectoryOffset=" + offset + ")");
      }

      int compressedSize = buffer.getInt(offset + 20);
      int uncompressedSize = buffer.getInt(offset + 24);
      int headerOffset = buffer.getInt(offset + 42);
      byte method = (byte)(buffer.getShort(offset + 10) & 0xffff);

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

      int entrySetIndex = probe(name, Xxh3.hash(tempNameBytes, 0, nameLengthInBytes - extraSuffixLength), entrySet);
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

  // returns index at which element is present; or if absent, (-i - 1) where i is location where element should be inserted
  private static int probe(String key, long keyHash, ImmutableZipEntry[] set) {
    // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
    int index = (int)((((int)keyHash & 0xffffffffL) * set.length) >>> 32);
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

  @Override
  public void close() throws Exception {
    ByteBuffer buffer = mappedBuffer;
    if (buffer != null) {
      mappedBuffer = null;
      ByteBufferCleaner.unmapBuffer(buffer);
    }
  }
}
