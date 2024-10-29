// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.ByteArrayCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import static java.nio.charset.StandardCharsets.US_ASCII;

public final class IOUtil {
  public static final int KiB = 1024;
  public static final int MiB = 1024 * 1024;
  public static final int GiB = 1024 * 1024 * 1024;

  @ApiStatus.Internal
  public static final ThreadLocal<Boolean> OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP = new ThreadLocal<Boolean>() {
    @Override
    public void set(Boolean value) {
      if (get() != null) {
        throw new RuntimeException("Reentrant access");
      }
      super.set(value);
    }
  };

  @ApiStatus.Internal
  public static final String SHARED_CACHES_PROP = "idea.shared.caches";

  /**
   * if false then storages will use {@link java.nio.ByteOrder#BIG_ENDIAN}
   */
  @ApiStatus.Internal
  public static boolean useNativeByteOrderForByteBuffers() {
    Boolean forced = OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.get();
    return forced == null || forced.booleanValue();
  }

  public static boolean isSharedCachesEnabled() {
    return SystemProperties.getBooleanProperty(SHARED_CACHES_PROP, false);
  }

  private static final int STRING_HEADER_SIZE = 1;
  private static final int STRING_LENGTH_THRESHOLD = 255;
  private static final String LONGER_THAN_64K_MARKER = "LONGER_THAN_64K";

  private IOUtil() { }

  public static String readString(@NotNull DataInput stream) throws IOException {
    try {
      int length = stream.readInt();
      if (length == -1) return null;
      if (length == 0) return "";

      byte[] bytes = new byte[length * 2];
      stream.readFully(bytes);
      return new String(bytes, 0, length * 2, StandardCharsets.UTF_16BE);
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  /** reads all bytes from the buffer, and creates UTF8 string from them */
  public static @NotNull String readString(@NotNull ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }


  public static void writeString(@Nullable String s, @NotNull DataOutput stream) throws IOException {
    writeCharSequence(s, stream);
  }

  public static void writeCharSequence(@Nullable CharSequence s, @NotNull DataOutput stream) throws IOException {
    if (s == null) {
      stream.writeInt(-1);
      return;
    }

    stream.writeInt(s.length());
    if (s.length() == 0) {
      return;
    }

    byte[] bytes = new byte[s.length() * 2];

    for (int i = 0, i2 = 0; i < s.length(); i++, i2 += 2) {
      char aChar = s.charAt(i);
      bytes[i2] = (byte)(aChar >>> 8 & 0xFF);
      bytes[i2 + 1] = (byte)(aChar & 0xFF);
    }

    stream.write(bytes);
  }

  public static void writeUTFTruncated(@NotNull DataOutput stream, @NotNull String text) throws IOException {
    // we should not compare number of symbols to 65635 -> it is number of bytes what should be compared
    // ? 4 bytes per symbol - rough estimation
    if (text.length() > 16383) {
      stream.writeUTF(text.substring(0, 16383));
    }
    else {
      stream.writeUTF(text);
    }
  }

  private static final ThreadLocalCachedValue<byte[]> ourReadWriteBuffersCache = new ThreadLocalCachedValue<byte[]>() {
    @Override
    protected byte @NotNull [] create() {
      return allocReadWriteUTFBuffer();
    }
  };

  public static void writeUTF(@NotNull DataOutput storage, @NotNull String value) throws IOException {
    writeUTFFast(ourReadWriteBuffersCache.getValue(), storage, value);
  }

  public static void writeUTF(@NotNull DataOutput storage, @NotNull CharSequence value) throws IOException {
    writeUTFFast(ourReadWriteBuffersCache.getValue(), storage, value);
  }

  public static String readUTF(@NotNull DataInput storage) throws IOException {
    return readUTFFast(ourReadWriteBuffersCache.getValue(), storage);
  }

  public static CharSequence readUTFCharSequence(@NotNull DataInput storage) throws IOException {
    return readUTFFastCharSequence(storage);
  }

  public static byte @NotNull [] allocReadWriteUTFBuffer() {
    return new byte[STRING_LENGTH_THRESHOLD + STRING_HEADER_SIZE];
  }

  public static void writeUTFFast(byte @NotNull [] buffer, @NotNull DataOutput storage, @NotNull String value) throws IOException {
    writeUTFFast(buffer, storage, (CharSequence)value);
  }

  public static void writeUTFFast(byte @NotNull [] buffer, @NotNull DataOutput storage, @NotNull CharSequence value) throws IOException {
    int len = value.length();
    if (len < STRING_LENGTH_THRESHOLD) {
      buffer[0] = (byte)len;
      boolean isAscii = true;
      for (int i = 0; i < len; i++) {
        char c = value.charAt(i);
        if (c >= 128) {
          isAscii = false;
          break;
        }
        buffer[i + STRING_HEADER_SIZE] = (byte)c;
      }
      if (isAscii) {
        storage.write(buffer, 0, len + STRING_HEADER_SIZE);
        return;
      }
    }
    storage.writeByte((byte)0xFF);

    try {
      storage.writeUTF(value.toString());
    }
    catch (UTFDataFormatException e) {
      storage.writeUTF(LONGER_THAN_64K_MARKER);
      writeCharSequence(value, storage);
    }
  }

  public static String readUTFFast(byte @NotNull [] buffer, @NotNull DataInput storage) throws IOException {
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      return readLongString(storage);
    }

    if (len == 0) return "";
    storage.readFully(buffer, 0, len);
    return new String(buffer, 0, len, StandardCharsets.ISO_8859_1);
  }

  private static @Nullable String readLongString(@NotNull DataInput storage) throws IOException {
    String result = storage.readUTF();
    if (LONGER_THAN_64K_MARKER.equals(result)) {
      return readString(storage);
    }
    return result;
  }

  public static CharSequence readUTFFastCharSequence(@NotNull DataInput storage) throws IOException {
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      return readLongString(storage);
    }

    if (len == 0) return "";
    byte[] data = new byte[len];
    storage.readFully(data, 0, len);
    return new ByteArrayCharSequence(data, 0, len);
  }

  public static boolean isAscii(@NotNull String str) {
    return isAscii((CharSequence)str);
  }

  public static boolean isAscii(@NotNull CharSequence str) {
    for (int i = 0, length = str.length(); i < length; ++i) {
      if (str.charAt(i) >= 128) return false;
    }
    return true;
  }

  public static boolean isAscii(char c) {
    return c < 128;
  }

  /**
   * @return true if _there are no files with such prefix exist_ -- e.g. if we delete nothing,
   * because there were no such files beforehand.
   */
  public static boolean deleteAllFilesStartingWith(@NotNull Path file) {
    String baseName = file.getFileName().toString();
    Path parentFile = file.getParent();
    if (parentFile == null) {
      return true;
    }

    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(parentFile)) {
      for (Path path : directoryStream) {
        if (path.getFileName().toString().startsWith(baseName)) {
          files.add(path);
        }
      }
    }
    catch (NoSuchFileException ignore) {
      return true;
    }
    catch (IOException ignore) {
      return false;
    }

    boolean ok = true;
    for (Path f : files) {
      try {
        FileUtil.delete(f);
      }
      catch (IOException ignore) {
        ok = false;
      }
    }
    return ok;
  }

  public static boolean deleteAllFilesStartingWith(@NotNull File file) {
    final String baseName = file.getName();
    File parentFile = file.getParentFile();
    final File[] files = parentFile != null ? parentFile.listFiles(pathname -> pathname.getName().startsWith(baseName)) : null;

    boolean ok = true;
    if (files != null) {
      for (File f : files) {
        ok &= FileUtil.delete(f);
      }
    }

    return ok;
  }

  public static <T> T openCleanOrResetBroken(@NotNull ThrowableComputable<T, ? extends IOException> factoryComputable,
                                             @NotNull Path file) throws IOException {
    return openCleanOrResetBroken(factoryComputable, () -> deleteAllFilesStartingWith(file));
  }

  public static <T> T openCleanOrResetBroken(@NotNull ThrowableComputable<T, ? extends IOException> factoryComputable,
                                             @NotNull File file) throws IOException {
    return openCleanOrResetBroken(factoryComputable, () -> deleteAllFilesStartingWith(file));
  }

  public static <T> T openCleanOrResetBroken(@NotNull ThrowableComputable<T, ? extends IOException> factoryComputable,
                                             @NotNull Runnable cleanupCallback) throws IOException {
    try {
      return factoryComputable.compute();
    }
    catch (IOException ex) {
      cleanupCallback.run();
    }

    return factoryComputable.compute();
  }

  /**
   * Consider to use {@link com.intellij.util.io.externalizer.StringCollectionExternalizer}.
   */
  public static void writeStringList(@NotNull DataOutput out, @NotNull Collection<String> list) throws IOException {
    DataInputOutputUtil.writeINT(out, list.size());
    for (String s : list) {
      writeUTF(out, s);
    }
  }

  /**
   * Consider to use {@link com.intellij.util.io.externalizer.StringCollectionExternalizer}.
   */
  public static @NotNull <C extends Collection<String>> C readStringCollection(@NotNull DataInput in,
                                                                               @NotNull IntFunction<? extends C> collectionGenerator)
    throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    C strings = collectionGenerator.apply(size);
    for (int i = 0; i < size; i++) {
      strings.add(readUTF(in));
    }
    return strings;
  }

  /**
   * Consider to use {@link com.intellij.util.io.externalizer.StringCollectionExternalizer}.
   */
  public static @NotNull List<String> readStringList(@NotNull DataInput in) throws IOException {
    return readStringCollection(in, ArrayList::new);
  }

  public static void closeSafe(@NotNull Logger log,
                               Closeable... closeables) {
    for (Closeable closeable : closeables) {
      if (closeable != null) {
        try {
          closeable.close();
        }
        catch (IOException e) {
          log.error(e);
        }
      }
    }
  }

  public static void closeSafe(@NotNull Logger log,
                               AutoCloseable... closeables) {
    for (AutoCloseable closeable : closeables) {
      if (closeable != null) {
        try {
          closeable.close();
        }
        catch (Exception e) {
          log.error(e);
        }
      }
    }
  }


  private static final byte[] ZEROES = new byte[64 * 1024];

  /**
   * Imitates 'fallocate' linux call: ensures file region [channel.size()..upUntilSize) is allocated on disk,
   * and zeroed. We can't call 'fallocate' directly, hence just write zeros into the channel.
   */
  public static void allocateFileRegion(@NotNull FileChannel channel,
                                        long upUntilSize) throws IOException {
    long channelSize = channel.size();
    if (channelSize < upUntilSize) {
      fillFileRegionWithZeros(channel, channelSize, upUntilSize);
    }
  }

  /**
   * Zero region [startingOffset..upUntilSize) -- i.e. upper limit exclusive.
   * File is expanded, if needed
   */
  public static void fillFileRegionWithZeros(@NotNull FileChannel channel,
                                             long startingOffset,
                                             long upUntilOffset) throws IOException {
    int stride = ZEROES.length;
    ByteBuffer zeros = ByteBuffer.wrap(ZEROES);
    for (long pos = startingOffset; pos < upUntilOffset; pos += stride) {
      int remainsToZero = Math.toIntExact(Math.min(stride, upUntilOffset - pos));
      zeros.clear().limit(remainsToZero);
      channel.write(zeros, pos);
    }
  }


  public static <T> byte[] toBytes(final T object,
                                   final @NotNull DataExternalizer<? super T> externalizer) throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (final DataOutputStream dos = new DataOutputStream(bos)) {
      externalizer.save(dos, object);
    }
    return bos.toByteArray();
  }

  public static <T> T fromBytes(final byte[] bytes,
                                final @NotNull DataExternalizer<? extends T> externalizer) throws IOException {
    final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    try (final DataInputStream dis = new DataInputStream(bis)) {
      return externalizer.read(dis);
    }
  }

  /**
   * @return string with buffer content (full: [0..capacity)), as-if it is byte[], formatted by Arrays.toString(byte[])
   * Method is for debug view, for reading string from bytebuffer use {@link #readString(ByteBuffer)}
   */
  public static String toString(final @NotNull ByteBuffer buffer) {
    final byte[] bytes = new byte[buffer.capacity()];
    final ByteBuffer slice = buffer.duplicate();
    slice.position(0)
      .limit(buffer.capacity());
    slice.get(bytes);
    return Arrays.toString(bytes);
  }

  public static @NotNull String toHexString(final @NotNull ByteBuffer buffer) {
    return toHexString(buffer, /*pageSize: */ -1);
  }

  public static @NotNull String toHexString(final @NotNull ByteBuffer buffer,
                                            final int pageSize) {
    final byte[] bytes = new byte[buffer.capacity()];
    final ByteBuffer slice = buffer.duplicate();
    slice.position(0)
      .limit(buffer.capacity());
    slice.get(bytes);
    return toHexString(bytes, pageSize);
  }

  public static @NotNull String toHexString(final byte[] bytes) {
    return toHexString(bytes, /*pageSize: */-1);
  }

  public static @NotNull String toHexString(final byte[] bytes,
                                            final int pageSize) {
    final StringBuilder sb = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
      final byte b = bytes[i];
      final int unsignedByte = Byte.toUnsignedInt(b);
      if (unsignedByte < 16) {//Integer.toHexString format it single-digit, which ruins blocks alignment
        sb.append("0");
      }
      sb.append(Integer.toHexString(unsignedByte));
      if (i < bytes.length - 1) {
        if (pageSize > 0 && i % pageSize == pageSize - 1) {
          sb.append('\n');
        }
        else {
          sb.append(' ');
        }
      }
    }
    return sb.toString();
  }

  /** Convert 4-chars ascii string into an int32 'magicWord' -- i.e. reserved header value used to identify a file format. */
  public static int asciiToMagicWord(@NotNull String ascii) {
    if (ascii.length() != 4) {
      throw new IllegalArgumentException("ascii[" + ascii + "] must be 4 ASCII chars long");
    }
    byte[] bytes = ascii.getBytes(US_ASCII);
    if (bytes.length != 4) {
      throw new IllegalArgumentException("ascii bytes [" + toHexString(bytes) + "].length must be 4");
    }

    return (Byte.toUnsignedInt(bytes[0]) << 24)
           | (Byte.toUnsignedInt(bytes[1]) << 16)
           | (Byte.toUnsignedInt(bytes[2]) << 8)
           | Byte.toUnsignedInt(bytes[3]);
  }

  public static String magicWordToASCII(int magicWord) {
    byte[] ascii = new byte[4];
    ascii[0] = (byte)((magicWord >> 24) & 0xFF);
    ascii[1] = (byte)((magicWord >> 16) & 0xFF);
    ascii[2] = (byte)((magicWord >> 8) & 0xFF);
    ascii[3] = (byte)((magicWord) & 0xFF);
    return new String(ascii, US_ASCII);
  }

  /**
   * Tries to wrap storageToWrap into another storage Out with the wrapperer.
   * If the wrapperer call fails -- close storageToWrap before propagating exception up the callstack.
   * (If the wrapperer call succeeded -- wrapping storage (Out) is now responsible for the closing of wrapped storage)
   */
  public static <Out, In extends AutoCloseable, E extends Throwable>
  Out wrapSafely(@NotNull In storageToWrap,
                 @NotNull ThrowableNotNullFunction<In, Out, E> wrapperer) throws E {
    try {
      return wrapperer.fun(storageToWrap);
    }
    catch (Throwable mainEx) {
      try {
        if (storageToWrap instanceof Unmappable) {
          Unmappable unmappable = (Unmappable)storageToWrap;
          unmappable.closeAndUnsafelyUnmap();
        }
        else {
          storageToWrap.close();
        }
      }
      catch (Throwable closeEx) {
        mainEx.addSuppressed(closeEx);
      }
      throw mainEx;
    }
  }


  private static final AtomicLong BITS_RESERVED_MEMORY_FIELD;

  static {
    //RC: counter-intuitively, but Direct ByteBuffers (seems to be) invisible to any public monitoring API.
    //    E.g. memoryMXBean.getNonHeapMemoryUsage() doesn't count memory occupied by direct ByteBuffers
    //    -- and neither do others memory-related MX-beans.
    //    java.nio.Bits.RESERVED_MEMORY is the best way I'm able to find:
    AtomicLong reservedMemoryCounter = null;
    try {
      Class<?> bitsClass = Class.forName("java.nio.Bits");
      Field reservedMemoryField = bitsClass.getDeclaredField("RESERVED_MEMORY");
      reservedMemoryField.setAccessible(true);
      reservedMemoryCounter = (AtomicLong)reservedMemoryField.get(null);
    }
    catch (Throwable t) {
      Logger log = Logger.getInstance(IOUtil.class);
      if (log.isDebugEnabled()) {
        log.warn("Can't get java.nio.Bits.RESERVED_MEMORY", t);
      }
    }

    BITS_RESERVED_MEMORY_FIELD = reservedMemoryCounter;
  }


  //MAYBE RC: this method + PageCacheUtils.maxDirectMemory() is better to move to some common utility class

  /** @return total size (bytes) of all direct {@link ByteBuffer}s allocated, or -1 if metric is not available */
  public static long directBuffersTotalAllocatedSize() {
    if (BITS_RESERVED_MEMORY_FIELD != null) {
      return BITS_RESERVED_MEMORY_FIELD.get();
    }
    else {
      return -1;
    }
  }
}


