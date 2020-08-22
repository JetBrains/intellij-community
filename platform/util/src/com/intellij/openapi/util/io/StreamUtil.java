// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class StreamUtil {
  private StreamUtil() { }

  /**
   * Copy stream. Use NetUtils.copyStreamContent(ProgressIndicator, ...) if you want use ProgressIndicator.
   *
   * @param inputStream source stream
   * @param outputStream destination stream
   * @return bytes copied
   */
  public static int copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    byte[] buffer = new byte[8 * 1024];
    int read, total = 0;
    while ((read = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, read);
      total += read;
    }
    return total;
  }

  public static byte @NotNull [] readBytes(@NotNull InputStream inputStream) throws IOException {
    UnsyncByteArrayOutputStream outputStream = new UnsyncByteArrayOutputStream();
    copy(inputStream, outputStream);
    return outputStream.toByteArray();
  }

  public static @NotNull String readText(@NotNull Reader reader) throws IOException {
    char[] chars = readChars(reader);
    return StringFactory.createShared(chars);
  }

  public static @NotNull String convertSeparators(@NotNull String s) {
    return StringFactory.createShared(convertSeparators(s.toCharArray()));
  }

  public static char @NotNull [] readTextAndConvertSeparators(@NotNull Reader reader) throws IOException {
    char[] chars = readChars(reader);
    return convertSeparators(chars);
  }

  private static char[] convertSeparators(char [] buffer) {
    int dst = 0;
    char prev = ' ';
    for (char c : buffer) {
      switch (c) {
        case'\r':
          buffer[dst++] = '\n';
          break;
        case'\n':
          if (prev != '\r') {
            buffer[dst++] = '\n';
          }
          break;
        default:
          buffer[dst++] = c;
          break;
      }
      prev = c;
    }

    if (dst == buffer.length) return buffer;
    char[] result = new char[dst];
    System.arraycopy(buffer, 0, result, 0, result.length);
    return result;
  }

  private static char[] readChars(Reader reader) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();
    char[] buffer = new char[2048];
    int read;
    while ((read = reader.read(buffer)) > 0) writer.write(buffer, 0, read);
    return writer.toCharArray();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated unfortunate name; please use {@link #copy(InputStream, OutputStream)} instead */
  @Deprecated
  public static int copyStreamContent(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    return copy(inputStream, outputStream);
  }

  /** @deprecated bad style (resource closing should be caller's responsibility); use {@link #readBytes(InputStream)} instead */
  @Deprecated
  public static byte @NotNull [] loadFromStream(@NotNull InputStream inputStream) throws IOException {
    UnsyncByteArrayOutputStream outputStream = new UnsyncByteArrayOutputStream();
    try {
      copy(inputStream, outputStream);
    }
    finally {
      inputStream.close();
    }
    return outputStream.toByteArray();
  }

  /** @deprecated bad style (resource closing should be caller's responsibility); use {@link #readText(Reader)} instead */
  @Deprecated
  public static @NotNull String readText(@NotNull InputStream inputStream) throws IOException {
    return readText(inputStream, StandardCharsets.UTF_8);
  }

  /** @deprecated bad style (resource closing should be caller's responsibility); use {@link #readText(Reader)} instead */
  @Deprecated
  public static @NotNull String readText(@NotNull InputStream inputStream, @NotNull String encoding) throws IOException {
    return readText(inputStream, Charset.forName(encoding));
  }

  /** @deprecated bad style (resource closing should be caller's responsibility); use {@link #readText(Reader)} instead */
  @Deprecated
  public static @NotNull String readText(@NotNull InputStream inputStream, @NotNull Charset encoding) throws IOException {
    byte[] data = loadFromStream(inputStream);
    return new String(data, encoding);
  }

  /** @deprecated unfortunate name; please use {@link #readText(Reader)} instead */
  @Deprecated
  public static @NotNull String readTextFrom(@NotNull Reader reader) throws IOException {
    return readText(reader);
  }

  /** @deprecated outdated pattern; use try-with-resources instead */
  @Deprecated
  public static void closeStream(@Nullable Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        Logger.getInstance(StreamUtil.class).error(e);
      }
    }
  }
  //</editor-fold>
}
