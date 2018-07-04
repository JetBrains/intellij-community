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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.text.StringFactory;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
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
      LZ4Compressor compressor = compressor();
      
      byte[] compressedOutputBuffer = spareBufferLocal.getBuffer(compressor.maxCompressedLength(length));
      int compressedSize = compressor.compress(bytes, start, length, compressedOutputBuffer, 0);
      if (compressedSize < length) {
        DataInputOutputUtil.writeINT(out, -compressedSize);
        DataInputOutputUtil.writeINT(out, length - compressedSize);
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

  public static int writeCompressedWithoutOriginalBufferLength(@NotNull DataOutput out, @NotNull byte[] bytes, int length) throws IOException {
    long started = DUMP_COMPRESSION_STATS ? System.nanoTime() : 0;

    LZ4Compressor compressor = compressor();
    final byte[] compressedOutputBuffer = spareBufferLocal.getBuffer(compressor.maxCompressedLength(length));
    int compressedSize = compressor.compress(bytes, 0, length, compressedOutputBuffer, 0);

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

  private static LZ4Compressor compressor() {
    return LZ4Factory.fastestJavaInstance().fastCompressor();
  }

  @NotNull
  public static byte[] readCompressedWithoutOriginalBufferLength(@NotNull DataInput in, int originalBufferLength) throws IOException {
    int size = DataInputOutputUtil.readINT(in);

    byte[] bytes = spareBufferLocal.getBuffer(size);
    in.readFully(bytes, 0, size);

    int decompressedRequests = myDecompressionRequests.incrementAndGet();
    long started = DUMP_COMPRESSION_STATS ? System.nanoTime() : 0;

    final byte[] decompressedResult = decompressor().decompress(bytes, 0, originalBufferLength);

    long doneTime = (DUMP_COMPRESSION_STATS ? System.nanoTime() : 0) - started;
    long decompressedSize = myDecompressedSize.addAndGet(size);
    long decompressedTime = myDecompressionTime.addAndGet(doneTime);
    if (DUMP_COMPRESSION_STATS && (decompressedRequests & 0x1fff)  == 0) {
      System.out.println("Decompressed " + decompressedRequests + " times, size: " + decompressedSize  + " for " + (decompressedTime / 1000000) + "ms");
    }

    return decompressedResult;
  }

  protected static LZ4FastDecompressor decompressor() {
    return LZ4Factory.fastestJavaInstance().fastDecompressor();
  }

  @NotNull
  public static byte[] readCompressed(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    if (size < 0) {
      size = -size;
      byte[] bytes = spareBufferLocal.getBuffer(size);
      int sizeUncompressed = DataInputOutputUtil.readINT(in) + size;
      in.readFully(bytes, 0, size);
      byte[] result = new byte[sizeUncompressed];
      int decompressed = decompressor().decompress(bytes, 0, result, 0, sizeUncompressed);
      assert decompressed == size;
      return result;
    }
    else {
      byte[] bytes = new byte[size];
      in.readFully(bytes);
      return bytes;
    }
  }

  private static final int STRING_COMPRESSION_THRESHOLD = 1024;

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
      BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream(length);
      @NotNull DataOutput out = new DataOutputStream(bytes);

      for (int i=0; i< length;i++) {
        char c = string.charAt(i);
        DataInputOutputUtil.writeINT(out, c);
      }

      LZ4Compressor compressor = compressor();
      int bytesWritten = bytes.size();
      ByteBuffer dest = ByteBuffer.wrap(spareBufferLocal.getBuffer(compressor.maxCompressedLength(bytesWritten) + 10));
      DataInputOutputUtil.writeINT(dest, length);
      DataInputOutputUtil.writeINT(dest, bytesWritten - length);
      compressor.compress(ByteBuffer.wrap(bytes.getInternalBuffer(), 0, bytesWritten), dest);
      
      return dest.position() < length * 2 ? Arrays.copyOf(dest.array(), dest.position()) : string;
    }
    catch (IOException e) {
      e.printStackTrace();
      return string;
    }
  }

  @NotNull
  public static CharSequence uncompressStringRawBytes(@NotNull Object compressed) {
    if (compressed instanceof CharSequence) return (CharSequence)compressed;
    
    ByteBuffer buffer = ByteBuffer.wrap((byte[])compressed);
    int len = DataInputOutputUtil.readINT(buffer);
    int uncompressedLength = DataInputOutputUtil.readINT(buffer) + len;
    
    ByteBuffer dest = ByteBuffer.wrap(spareBufferLocal.getBuffer(uncompressedLength), 0, uncompressedLength);
    decompressor().decompress(buffer, dest);
    dest.rewind();
    
    char[] chars = new char[len];

    for (int i=0; i<len; i++) {
      int c = DataInputOutputUtil.readINT(dest);
      chars[i] = (char)c;
    }
    return StringFactory.createShared(chars);
  }
}
