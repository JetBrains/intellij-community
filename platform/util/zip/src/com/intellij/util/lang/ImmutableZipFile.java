// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
  public static final int EOCD = 0x6054B50;

  private static final int INDEX_FORMAT_VERSION = 4;
  private static final int COMMENT_SIZE = 5;

  private final Ikv.SizeAwareIkv ikv;
  private final int nameDataPosition;
  private volatile String[] names;
  public final long[] classPackages;
  public final long[] resourcePackages;

  private ImmutableZipFile(Ikv.SizeAwareIkv ikv,
                           long[] classPackages,
                           long[] resourcePackages,
                           int nameDataPosition) {
    this.ikv = ikv;
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

  public synchronized String[] getOrComputeNames() {
    String[] names = this.names;
    if (names != null) {
      return names;
    }

    // read names
    // assume that file name is not greater than ~4 KiB
    byte[] tempNameBytes = new byte[4096];
    int entryCount = ikv.getEntryCount();
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
    for (String name : names) {
      if (name.length() >= minNameLength && name.charAt(dir.length()) == '/' && name.startsWith(dir) && nameFilter.test(name)) {
        // DirectByteBufferBackedInputStream is not pooled - no need to close
        consumer.accept(name, getInputStream(name));
      }
    }
  }

  @Override
  public @Nullable InputStream getInputStream(@NotNull String path) throws IOException {
    ByteBuffer byteBuffer = ikv.getValue(Xxh3.hash(path.getBytes(StandardCharsets.UTF_8)));
    return byteBuffer == null ? null : new DirectByteBufferBackedInputStream(byteBuffer, false);
  }

  @Override
  public byte @Nullable [] getData(@NotNull String path) throws IOException {
    return ikv.getByteArray(Xxh3.hash(path.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public ByteBuffer getByteBuffer(@NotNull String path) throws IOException {
    return ikv.getValue(Xxh3.hash(path.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public @Nullable ZipResource getResource(String path) {
    long pair = ikv.getOffsetAndSize(Xxh3.hash(path.getBytes(StandardCharsets.UTF_8)));
    return pair == -1 ? null : new MyZipResource(pair, ikv, path);
  }

  private static final class MyZipResource implements ZipResource {
    private final long entry;
    private final Ikv.SizeAwareIkv ikv;
    private final String path;

    MyZipResource(long pair, Ikv.SizeAwareIkv ikv, String path) {
      this.entry = pair;
      this.ikv = ikv;
      this.path = path;
    }

    @Override
    public int getUncompressedSize() {
      return ikv.getSizeByValue(entry);
    }

    @Override
    public @NotNull String getPath() {
      return path;
    }

    @Override
    public @NotNull ByteBuffer getByteBuffer() {
      return ikv.getByteBufferByValue(entry);
    }

    @Override
    public byte @NotNull [] getData() {
      return ikv.getByteArrayByValue(entry);
    }

    @Override
    public @NotNull InputStream getInputStream() {
      return new DirectByteBufferBackedInputStream(ikv.getByteBufferByValue(entry), false);
    }
  }

  @Override
  public void releaseBuffer(ByteBuffer buffer) {
    // data is never compressed, and buffer is always a mapped byte buffer - no need to release
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

    if (offset == 0) {
      // empty file
      return new EmptyZipFile();
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
    Ikv.SizeAwareIkv ikv = (Ikv.SizeAwareIkv)Ikv.loadIkv(buffer, indexDataEnd);
    buffer.position(indexDataEnd);
    // read package class and resource hashes
    long[] classPackages = new long[buffer.getInt()];
    long[] resourcePackages = new long[buffer.getInt()];
    LongBuffer longBuffer = buffer.asLongBuffer();
    longBuffer.get(classPackages);
    longBuffer.get(resourcePackages);

    int nameDataPosition = indexDataEnd + Long.BYTES + (longBuffer.position() * Long.BYTES);
    buffer.clear();
    return new ImmutableZipFile(ikv, classPackages, resourcePackages, nameDataPosition);
  }

  @Override
  public void close() throws Exception {
    ikv.close();
  }
}