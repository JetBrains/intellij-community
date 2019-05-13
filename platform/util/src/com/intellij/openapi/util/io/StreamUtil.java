/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;

public class StreamUtil {
  private static final Logger LOG = Logger.getInstance(StreamUtil.class);

  private StreamUtil() {
  }

  /**
   * Copy stream. Use NetUtils.copyStreamContent(ProgressIndicator, ...) if you want use ProgressIndicator.
   *
   * @param inputStream source stream
   * @param outputStream destination stream
   * @return bytes copied
   */
  public static int copyStreamContent(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    final byte[] buffer = new byte[10 * 1024];
    int count;
    int total = 0;
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      total += count;
    }
    return total;
  }

  @NotNull
  public static byte[] loadFromStream(@NotNull InputStream inputStream) throws IOException {
    final UnsyncByteArrayOutputStream outputStream = new UnsyncByteArrayOutputStream();
    try {
      copyStreamContent(inputStream, outputStream);
    }
    finally {
      inputStream.close();
    }
    return outputStream.toByteArray();
  }

  /**
   * @deprecated depends on the default encoding, use StreamUtil#readText(java.io.InputStream, String) instead
   */
  @Deprecated
  @NotNull
  public static String readText(@NotNull InputStream inputStream) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    return new String(data);
  }

  @NotNull
  public static String readText(@NotNull InputStream inputStream, @NotNull String encoding) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    return new String(data, encoding);
  }
  @NotNull
  public static String readText(@NotNull InputStream inputStream, @NotNull Charset encoding) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    return new String(data, encoding);
  }

  @NotNull
  public static String convertSeparators(@NotNull String s) {
    return StringFactory.createShared(convertSeparators(s.toCharArray()));
  }

  @NotNull
  public static char[] readTextAndConvertSeparators(@NotNull Reader reader) throws IOException {
    char[] buffer = readText(reader);

    return convertSeparators(buffer);
  }

  @NotNull
  private static char[] convertSeparators(@NotNull char[] buffer) {
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

    if (dst == buffer.length) {
      return buffer;
    }
    char[] result = new char[dst];
    System.arraycopy(buffer, 0, result, 0, result.length);
    return result;
  }

  @NotNull
  public static String readTextFrom(@NotNull Reader reader) throws IOException {
    return StringFactory.createShared(readText(reader));
  }

  @NotNull
  private static char[] readText(@NotNull Reader reader) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();

    char[] buffer = new char[2048];
    while (true) {
      int read = reader.read(buffer);
      if (read < 0) break;
      writer.write(buffer, 0, read);
    }

    return writer.toCharArray();
  }

  public static void closeStream(@Nullable Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
