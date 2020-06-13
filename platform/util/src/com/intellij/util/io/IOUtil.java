// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

public final class IOUtil {
  @SuppressWarnings("SpellCheckingInspection") public static final boolean BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER =
    SystemProperties.getBooleanProperty("idea.bytebuffers.use.native.byte.order", true);

  private static final int STRING_HEADER_SIZE = 1;
  private static final int STRING_LENGTH_THRESHOLD = 255;
  private static final String LONGER_THAN_64K_MARKER = "LONGER_THAN_64K";

  private IOUtil() {}

  public static String readString(@NotNull DataInput stream) throws IOException {
    try {
      int length = stream.readInt();
      if (length == -1) return null;
      if (length == 0) return "";

      byte[] bytes = new byte[length * 2];
      stream.readFully(bytes);
      return new String(bytes, 0, length * 2, CharsetToolkit.UTF_16BE_CHARSET);
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  public static void writeString(@Nullable String s, @NotNull DataOutput stream) throws IOException {
    if (s == null) {
      stream.writeInt(-1);
      return;
    }

    stream.writeInt(s.length());
    if (s.isEmpty()) {
      return;
    }

    char[] chars = s.toCharArray();
    byte[] bytes = new byte[chars.length * 2];

    for (int i = 0, i2 = 0; i < chars.length; i++, i2 += 2) {
      char aChar = chars[i];
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

  public static String readUTF(@NotNull DataInput storage) throws IOException {
    return readUTFFast(ourReadWriteBuffersCache.getValue(), storage);
  }

  public static byte @NotNull [] allocReadWriteUTFBuffer() {
    return new byte[STRING_LENGTH_THRESHOLD + STRING_HEADER_SIZE];
  }

  public static void writeUTFFast(byte @NotNull [] buffer, @NotNull DataOutput storage, @NotNull String value) throws IOException {
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
      storage.writeUTF(value);
    }
    catch (UTFDataFormatException e) {
      storage.writeUTF(LONGER_THAN_64K_MARKER);
      writeString(value, storage);
    }
  }

  public static String readUTFFast(byte @NotNull [] buffer, @NotNull DataInput storage) throws IOException {
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      String result = storage.readUTF();
      if (LONGER_THAN_64K_MARKER.equals(result)) {
        return readString(storage);
      }

      return result;
    }

    if (len == 0) return "";
    storage.readFully(buffer, 0, len);

    return new String(buffer, 0, len, StandardCharsets.ISO_8859_1);
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

  public static void syncStream(@NotNull OutputStream stream) throws IOException {
    stream.flush();

    try {
      Field outField = FilterOutputStream.class.getDeclaredField("out");
      outField.setAccessible(true);
      while (stream instanceof FilterOutputStream) {
        Object o = outField.get(stream);
        if (o instanceof OutputStream) {
          stream = (OutputStream)o;
        }
        else {
          break;
        }
      }
      if (stream instanceof FileOutputStream) {
        ((FileOutputStream)stream).getFD().sync();
      }
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T openCleanOrResetBroken(@NotNull ThrowableComputable<T, ? extends IOException> factoryComputable,
                                             @NotNull final Path file) throws IOException {
    return openCleanOrResetBroken(factoryComputable, file.toFile());
  }

  public static <T> T openCleanOrResetBroken(@NotNull ThrowableComputable<T, ? extends IOException> factoryComputable,
                                             @NotNull final File file) throws IOException {
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
  @NotNull
  public static <C extends Collection<String>> C readStringCollection(@NotNull DataInput in, @NotNull IntFunction<C> generator) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    C strings = generator.apply(size);
    for (int i = 0; i < size; i++) {
      strings.add(readUTF(in));
    }
    return strings;
  }

  /**
   * Consider to use {@link com.intellij.util.io.externalizer.StringCollectionExternalizer}.
   */
  @NotNull
  public static List<String> readStringList(@NotNull DataInput in) throws IOException {
    return readStringCollection(in, ArrayList::new);
  }

  public static void closeSafe(@NotNull Logger log, Closeable... closeables) {
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
}