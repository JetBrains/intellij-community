// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.io.DataOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class CompressionUtil {
  private static final int COMPRESSION_THRESHOLD = 64;
  private static final ThreadLocalCachedByteArray spareBufferLocal = new ThreadLocalCachedByteArray();
  private static final LZ4Compressor compressor;
  private static final LZ4FastDecompressor decompressor;

  static {
    if (Boolean.getBoolean("idea.use.native.compression")) {
      LZ4Factory factory = LZ4Factory.fastestInstance();
      compressor = factory.fastCompressor();
      decompressor = factory.fastDecompressor();
    }
    else {
      LZ4Compressor c = null;
      LZ4FastDecompressor d = null;
      try {
        // java 9+ is required - util still has java 8 level
        Class<?> cClass = CompressionUtil.class.getClassLoader().loadClass("com.intellij.util.io.LZ4Compressor");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        c = (LZ4Compressor)lookup.findStaticGetter(cClass, "INSTANCE", cClass).invoke();
        Class<?> dClass = CompressionUtil.class.getClassLoader().loadClass("com.intellij.util.io.LZ4Decompressor");
        d = (LZ4FastDecompressor)lookup.findStaticGetter(dClass, "INSTANCE", dClass).invoke();
      }
      catch (Throwable ignore) {
      }

      if (c == null || d == null) {
        LZ4Factory factory = LZ4Factory.fastestJavaInstance();
        compressor = factory.fastCompressor();
        decompressor = factory.fastDecompressor();
      }
      else {
        compressor = c;
        decompressor = d;
      }
    }
  }

  private static LZ4Compressor compressor() {
    return compressor;
  }

  private static LZ4FastDecompressor decompressor() {
    return decompressor;
  }

  public static int writeCompressed(@NotNull DataOutput out, byte @NotNull [] bytes, int start, int length) throws IOException {
    if (length > COMPRESSION_THRESHOLD) {
      LZ4Compressor compressor = compressor();

      byte[] compressedOutputBuffer = spareBufferLocal.getBuffer(compressor.maxCompressedLength(length));
      int compressedSize = compressor.compress(bytes, start, length, compressedOutputBuffer, 0);
      if (compressedSize < length) {
        int val = -compressedSize;
        DataInputOutputUtilRt.writeINT(out, val);
        DataInputOutputUtilRt.writeINT(out, length - compressedSize);
        out.write(compressedOutputBuffer, 0, compressedSize);
        return compressedSize;
      }
    }
    DataInputOutputUtilRt.writeINT(out, length);
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

  public static int writeCompressedWithoutOriginalBufferLength(@NotNull DataOutput out, byte @NotNull [] bytes, int length) throws IOException {
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

    DataInputOutputUtilRt.writeINT(out, compressedSize);
    out.write(compressedOutputBuffer, 0, compressedSize);

    return compressedSize;
  }

  public static byte @NotNull [] readCompressedWithoutOriginalBufferLength(@NotNull DataInput in, int originalBufferLength) throws IOException {
    int size = DataInputOutputUtilRt.readINT(in);

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

  public static byte @NotNull [] readCompressed(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtilRt.readINT(in);
    if (size < 0) {
      size = -size;
      byte[] bytes = spareBufferLocal.getBuffer(size);
      int sizeUncompressed = DataInputOutputUtilRt.readINT(in) + size;
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

  public static @NotNull Object compressStringRawBytes(@NotNull CharSequence string) {
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
        DataInputOutputUtilRt.writeINT(out, c);
      }

      LZ4Compressor compressor = compressor();
      int bytesWritten = bytes.size();
      ByteBuffer dest = ByteBuffer.wrap(spareBufferLocal.getBuffer(compressor.maxCompressedLength(bytesWritten) + 10));
      DataInputOutputUtilRt.writeINT(dest, length);
      DataInputOutputUtilRt.writeINT(dest, bytesWritten - length);
      compressor.compress(ByteBuffer.wrap(bytes.getInternalBuffer(), 0, bytesWritten), dest);

      return dest.position() < length * 2 ? Arrays.copyOf(dest.array(), dest.position()) : string;
    }
    catch (IOException e) {
      e.printStackTrace();
      return string;
    }
  }

  public static @NotNull CharSequence uncompressStringRawBytes(@NotNull Object compressed) {
    if (compressed instanceof CharSequence) return (CharSequence)compressed;

    ByteBuffer buffer = ByteBuffer.wrap((byte[])compressed);
    int len = DataInputOutputUtilRt.readINT(buffer);
    int uncompressedLength = DataInputOutputUtilRt.readINT(buffer) + len;

    ByteBuffer dest = ByteBuffer.wrap(spareBufferLocal.getBuffer(uncompressedLength), 0, uncompressedLength);
    decompressor().decompress(buffer, dest);
    dest.rewind();

    char[] chars = new char[len];

    for (int i=0; i<len; i++) {
      int c = DataInputOutputUtilRt.readINT(dest);
      chars[i] = (char)c;
    }
    return new String(chars);
  }
}
