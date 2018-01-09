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
package com.intellij.util;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.text.StringFactory;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.iq80.snappy.CorruptionException;
import org.iq80.snappy.Snappy;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Maxim.Mossienko
 */
public class CompressionUtil {
  private static final int COMPRESSION_THRESHOLD = 64;
  private static final ThreadLocalCachedByteArray spareBufferLocal = new ThreadLocalCachedByteArray();

  public static int writeCompressed(@NotNull DataOutput out, @NotNull byte[] bytes, int start, int length) throws IOException {
    if (length > COMPRESSION_THRESHOLD) {
      byte[] compressedOutputBuffer = spareBufferLocal.getBuffer(Snappy.maxCompressedLength(length));
      int compressedSize = Snappy.compress(bytes, start, length, compressedOutputBuffer, 0);
      if (compressedSize < length) {
        DataInputOutputUtil.writeINT(out, -compressedSize);
        out.write(compressedOutputBuffer, 0, compressedSize);
        return compressedSize;
      }
    }
    DataInputOutputUtil.writeINT(out, length);
    out.write(bytes, start, length);
    return length;
  }

  private static final AtomicInteger myCompressionRequests = new AtomicInteger();
  private static final AtomicLong myCompressionTime = new AtomicLong();
  private static final AtomicInteger myDecompressionRequests = new AtomicInteger();
  private static final AtomicLong myDecompressionTime = new AtomicLong();
  private static final AtomicLong myDecompressedSize = new AtomicLong();
  private static final AtomicLong mySizeBeforeCompression = new AtomicLong();
  private static final AtomicLong mySizeAfterCompression = new AtomicLong();

  public static final boolean DUMP_COMPRESSION_STATS = SystemProperties.getBooleanProperty("idea.dump.compression.stats", false);
  public static final boolean USE_SNAPPY = SystemProperties.getBooleanProperty("idea.use.snappy", false);

  public static int writeCompressedWithoutOriginalBufferLength(@NotNull DataOutput out, @NotNull byte[] bytes, int length) throws IOException {
    long started = DUMP_COMPRESSION_STATS ? System.nanoTime() : 0;

    final byte[] compressedOutputBuffer;
    int compressedSize;

    if (USE_SNAPPY) {
      compressedOutputBuffer = spareBufferLocal.getBuffer(Snappy.maxCompressedLength(length));
      compressedSize = Snappy.compress(bytes, 0, length, compressedOutputBuffer, 0);
    } else {
      LZ4Compressor compressor = LZ4Factory.fastestJavaInstance().fastCompressor();
      compressedOutputBuffer = spareBufferLocal.getBuffer(compressor.maxCompressedLength(length));
      compressedSize = compressor.compress(bytes, 0, length, compressedOutputBuffer, 0);
    }

    final long time = (DUMP_COMPRESSION_STATS ? System.nanoTime() : 0) - started;
    mySizeAfterCompression.addAndGet(compressedSize);
    mySizeBeforeCompression.addAndGet(length);
    int requests = myCompressionRequests.incrementAndGet();
    long l = myCompressionTime.addAndGet(time);

    if (DUMP_COMPRESSION_STATS && (requests & 0x1fff)  == 0) {
      System.out.println("Compressed " + requests + " times, size:" + mySizeBeforeCompression + "->" + mySizeAfterCompression + " for " + (l  / 1000000) + "ms");
    }

    DataInputOutputUtil.writeINT(out, compressedSize);
    out.write(compressedOutputBuffer, 0, compressedSize);

    return compressedSize;
  }

  @NotNull
  public static byte[] readCompressedWithoutOriginalBufferLength(@NotNull DataInput in, int originalBufferLength) throws IOException {
    int size = DataInputOutputUtil.readINT(in);

    byte[] bytes = spareBufferLocal.getBuffer(size);
    in.readFully(bytes, 0, size);

    int decompressedRequests = myDecompressionRequests.incrementAndGet();
    long started = DUMP_COMPRESSION_STATS ? System.nanoTime() : 0;

    final byte[] decompressedResult;

    if (USE_SNAPPY) {
      decompressedResult = Snappy.uncompress(bytes, 0, size);
    } else {
      decompressedResult = LZ4Factory.fastestJavaInstance().fastDecompressor().decompress(bytes, 0, originalBufferLength);
    }

    long doneTime = (DUMP_COMPRESSION_STATS ? System.nanoTime() : 0) - started;
    long decompressedSize = myDecompressedSize.addAndGet(size);
    long decompressedTime = myDecompressionTime.addAndGet(doneTime);
    if (DUMP_COMPRESSION_STATS && (decompressedRequests & 0x1fff)  == 0) {
      System.out.println("Decompressed " + decompressedRequests + " times, size: " + decompressedSize  + " for " + (decompressedTime / 1000000) + "ms");
    }

    return decompressedResult;
  }

  @NotNull
  public static byte[] readCompressed(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    if (size < 0) {
      byte[] bytes = spareBufferLocal.getBuffer(-size);
      in.readFully(bytes, 0, -size);
      return Snappy.uncompress(bytes, 0, -size);
    }
    else {
      byte[] bytes = new byte[size];
      in.readFully(bytes);
      return bytes;
    }
  }

  private static final int STRING_COMPRESSION_THRESHOLD = 1024;

  @NotNull
  public static CharSequence uncompressCharSequence(@NotNull Object string, @NotNull Charset charset) {
    if (string instanceof CharSequence) return (CharSequence)string;
    byte[] b = (byte[])string;
    try {
      int uncompressedLength = Snappy.getUncompressedLength(b, 0);
      byte[] bytes = spareBufferLocal.getBuffer(uncompressedLength);
      int bytesLength = Snappy.uncompress(b, 0, b.length, bytes, 0);
      return new String(bytes, 0, bytesLength, charset);
    }
    catch (CorruptionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NotNull
  public static Object compressCharSequence(@NotNull CharSequence string, @NotNull Charset charset) {
    if (string.length() < STRING_COMPRESSION_THRESHOLD) {
      if (string instanceof CharBuffer && ((CharBuffer)string).capacity() > STRING_COMPRESSION_THRESHOLD) {
        string = string.toString();   // shrink to size
      }
      return string;
    }
    try {
      return Snappy.compress(string.toString().getBytes(charset));
    }
    catch (CorruptionException ex) {
      ex.printStackTrace();
      return string;
    }
  }

  @NotNull
  public static Object compressStringRawBytes(@NotNull CharSequence string) {
    int length = string.length();
    if (length < STRING_COMPRESSION_THRESHOLD) {
      if (string instanceof CharBuffer && ((CharBuffer)string).capacity() > STRING_COMPRESSION_THRESHOLD) {
        string = string.toString();   // shrink to size
      }
      return string;
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(length);
      @NotNull DataOutput out = new DataOutputStream(bytes);

      DataInputOutputUtil.writeINT(out, length);
      for (int i=0; i< length;i++) {
        char c = string.charAt(i);
        DataInputOutputUtil.writeINT(out, c);
      }
      byte[] compressedBytes = Snappy.compress(bytes.toByteArray());
      return compressedBytes.length < length * 2 ? compressedBytes : string;
    }
    catch (CorruptionException ex) {
      ex.printStackTrace();
      return string;
    }
    catch (IOException e) {
      e.printStackTrace();
      return string;
    }
  }

  @NotNull
  public static CharSequence uncompressStringRawBytes(@NotNull Object compressed) {
    if (compressed instanceof CharSequence) return (CharSequence)compressed;
    byte[] b = (byte[])compressed;
    try {
      int uncompressedLength = Snappy.getUncompressedLength(b, 0);
      byte[] bytes = spareBufferLocal.getBuffer(uncompressedLength);
      int bytesLength = Snappy.uncompress(b, 0, b.length, bytes, 0);
      ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes, 0, bytesLength);
      @NotNull DataInput in = new DataInputStream(byteStream);

      int len = DataInputOutputUtil.readINT(in);
      char[] chars = new char[len];

      for (int i=0; i<len; i++) {
        int c = DataInputOutputUtil.readINT(in);
        chars[i] = (char)c;
      }
      return StringFactory.createShared(chars);
    }
    catch (CorruptionException ex) {
      throw new RuntimeException(ex);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
