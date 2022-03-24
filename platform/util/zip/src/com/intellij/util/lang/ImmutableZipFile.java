// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ikv.Ikv;
import org.jetbrains.ikv.RecSplitSettings;
import org.jetbrains.ikv.UniversalHash;
import org.jetbrains.xxh3.Xxh3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.zip.ZipException;

@ApiStatus.Internal
public final class ImmutableZipFile implements ZipFile {
  public static final int MIN_EOCD_SIZE = 22;
  public static final int EOCD = 0x6054B50;

  private static final int INDEX_FORMAT_VERSION = 3;
  private static final int COMMENT_SIZE = 5;

  private final Ikv.SizeAwareIkv<String> ikv;
  private final int nameDataPosition;
  private volatile String[] names;
  public final long[] classPackages;
  public final long[] resourcePackages;
  private final long[] hashes;

  private ImmutableZipFile(Ikv.SizeAwareIkv<String> ikv,
                           long[] hashes,
                           long[] classPackages,
                           long[] resourcePackages,
                           int nameDataPosition) {
    this.ikv = ikv;
    this.hashes = hashes;
    this.nameDataPosition = nameDataPosition;
    this.classPackages = classPackages;
    this.resourcePackages = resourcePackages;
  }

  public static @NotNull ZipFile load(@NotNull Path file) throws IOException {
    return load(file, false);
  }

  static @NotNull ZipFile load(@NotNull Path file, boolean forceNonIkv) throws IOException {
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
      return populateFromCentralDirectory(mappedBuffer, fileSize, forceNonIkv);
    }
    catch (IOException e) {
      throw new IOException(file.toString(), e);
    }
  }

  private int getIndex(String path) {
    long hashCode = Xxh3.hash(path);
    int index = ikv.evaluator.evaluate(path, hashCode, UniversalHash.StringHash.INSTANCE);
    if (index < 0) {
      return -1;
    }
    if (hashes[index] != hashCode) {
      return -1;
    }
    return index;
  }

  public synchronized String[] getOrComputeNames() {
    String[] names = this.names;
    if (names != null) {
      return names;
    }

    // read names
    // assume that file name is not greater than ~4 KiB
    byte[] tempNameBytes = new byte[4096];
    int entryCount = hashes.length;
    names = new String[entryCount];
    short[] nameLengthInBytes = new short[entryCount];
    ByteBuffer buffer = ikv.getMappedBufferAt(nameDataPosition);
    buffer.asShortBuffer().get(nameLengthInBytes);
    buffer.position(buffer.position() + (entryCount * Short.BYTES));
    for (int i = 0; i < entryCount; i++) {
      short sizeInBytes = nameLengthInBytes[i];
      buffer.get(tempNameBytes, 0, sizeInBytes);
      names[i] = new String(tempNameBytes, 0, sizeInBytes, StandardCharsets.UTF_8);
    }

    this.names = names;
    return names;
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> nameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    String[] names = this.names;
    if (names == null) {
      names = getOrComputeNames();
    }

    int minNameLength = dir.length() + 2;
    for (int i = 0, n = names.length; i < n; i++) {
      String name = names[i];
      if (name.length() >= minNameLength && name.charAt(dir.length()) == '/' && name.startsWith(dir) && nameFilter.test(name)) {
        try (InputStream stream = new DirectByteBufferBackedInputStream(ikv.getByteBufferAt(i), false)) {
          consumer.accept(name, stream);
        }
      }
    }
  }

  @Override
  public @Nullable InputStream getInputStream(@NotNull String path) throws IOException {
    int index = getIndex(path);
    if (index < 0) {
      return null;
    }
    return new DirectByteBufferBackedInputStream(ikv.getByteBufferAt(index), false);
  }

  @Override
  public byte @Nullable [] getData(@NotNull String path) throws IOException {
    int index = getIndex(path);
    if (index < 0) {
      return null;
    }
    return ikv.getByteArrayAt(index);
  }

  @Override
  public ByteBuffer getByteBuffer(@NotNull String path) throws IOException {
    int index = getIndex(path);
    if (index < 0) {
      return null;
    }
    return ikv.getByteBufferAt(index);
  }

  @Override
  public @Nullable ZipResource getResource(String path) {
    int index = getIndex(path);
    return index < 0 ? null : new MyZipResource(index, ikv, path);
  }

  private static final class MyZipResource implements ZipResource {
    private final int index;
    private final Ikv.SizeAwareIkv<String> ikv;
    private final String path;

    MyZipResource(int index, Ikv.SizeAwareIkv<String> ikv, String path) {
      this.index = index;
      this.ikv = ikv;
      this.path = path;
    }

    @Override
    public int getUncompressedSize() {
      return ikv.getSizeAt(index);
    }

    @Override
    public @NotNull String getPath() {
      return path;
    }

    @Override
    public @NotNull ByteBuffer getByteBuffer() {
      return ikv.getByteBufferAt(index);
    }

    @Override
    public byte @NotNull [] getData() {
      return ikv.getByteArrayAt(index);
    }

    @Override
    public @NotNull InputStream getInputStream() {
      return new DirectByteBufferBackedInputStream(ikv.getByteBufferAt(index), false);
    }
  }

  @Override
  public void releaseBuffer(ByteBuffer buffer) {
    // data is never compressed and buffer is always a mapped byte buffer - no need to release
  }

  private static @NotNull ZipFile populateFromCentralDirectory(ByteBuffer buffer, int fileSize, boolean forceNonIkv) throws IOException {
    // https://en.wikipedia.org/wiki/ZIP_(file_format)
    int offset = fileSize - MIN_EOCD_SIZE;

    boolean finished = false;

    // first, EOCD
    for (; offset >= 0; offset--) {
      if (buffer.getInt(offset) == EOCD) {
        finished = true;
        break;
      }
    }
    if (!finished) {
      throw new ZipException("Archive is not a ZIP archive");
    }

    boolean isZip64 = true;
    if (buffer.getInt(offset - 20) == 0x07064b50) {
      offset = (int)buffer.getLong(offset - (20 - 8));
      assert buffer.getInt(offset) == 0x06064b50;
    }
    else {
      isZip64 = false;
    }

    int entryCount;
    int centralDirSize;
    int centralDirPosition;
    int commentSize;
    int commentVersion;
    int indexDataEnd = -1;
    if (isZip64) {
      entryCount = (int)buffer.getLong(offset + 32);
      centralDirSize = (int)buffer.getLong(offset + 40);
      centralDirPosition = (int)buffer.getLong(offset + 48);

      commentSize = (int)(buffer.getLong(offset + 4) + 12) - 56;
      commentVersion = commentSize == COMMENT_SIZE ? buffer.get(offset + 56) : 0;
      if (commentVersion == INDEX_FORMAT_VERSION) {
        indexDataEnd = buffer.getInt(offset + 56 + 1);
      }
    }
    else {
      entryCount = buffer.getShort(offset + 10) & 0xffff;
      centralDirSize = buffer.getInt(offset + 12);
      centralDirPosition = buffer.getInt(offset + 16);

      commentSize = buffer.getShort(offset + 20);
      commentVersion = commentSize == COMMENT_SIZE ? buffer.get(offset + 22) : 0;
      if (commentVersion == INDEX_FORMAT_VERSION) {
        indexDataEnd = buffer.getInt(offset + 22 + Byte.BYTES /* index format version size */);
      }
    }

    if (forceNonIkv || commentVersion != INDEX_FORMAT_VERSION || entryCount == 0) {
      return HashMapZipFile.createHashMapZipFile(buffer, fileSize, entryCount, centralDirSize, centralDirPosition);
    }

    buffer.position(indexDataEnd);
    Ikv.SizeAwareIkv<String> ikv = (Ikv.SizeAwareIkv<String>)Ikv.loadIkv(buffer,
                                                                         UniversalHash.StringHash.INSTANCE,
                                                                         RecSplitSettings.DEFAULT_SETTINGS);

    buffer.position(indexDataEnd);
    // read package class and resource hashes
    long[] classPackages = new long[buffer.getInt()];
    long[] resourcePackages = new long[buffer.getInt()];
    LongBuffer longBuffer = buffer.asLongBuffer();
    longBuffer.get(classPackages);
    longBuffer.get(resourcePackages);
    buffer.position(buffer.position() + (longBuffer.position() * Long.BYTES));

    // read fingerprints
    long[] hashes = new long[buffer.getInt()];
    buffer.asLongBuffer().get(hashes);
    buffer.position(buffer.position() + (hashes.length * Long.BYTES));

    int nameDataPosition = buffer.position();
    buffer.clear();
    return new ImmutableZipFile(ikv, hashes, classPackages, resourcePackages, nameDataPosition);
  }

  @Override
  public void close() throws Exception {
    ikv.close();
  }
}
