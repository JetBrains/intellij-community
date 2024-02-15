// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.content;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.LZ4Compressor;
import com.intellij.util.io.LZ4Decompressor;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import net.jpountz.lz4.LZ4Exception;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Abstracts out content algorithm of compression for content storage
 */
public interface CompressingAlgo {

  int ZIP_ID = 1;
  int LZ4_ID = 2;
  int NONE_ID = 3;

  /** ID unique identifying algo -- for storage versioning purposes */
  int algoID();


  /**
   * @return true if input should be compressed, false otherwise.
   * If this method returns false -- {@link #compress(ByteArraySequence)} method should NOT be called for the input
   */
  boolean shouldCompress(@NotNull ByteArraySequence input);

  ByteArraySequence compress(@NotNull ByteArraySequence input) throws IOException;

  /**
   * Decompresses bytes remaining in bufferWithCompressedData (i.e. position..limit) into bufferForDecompression.
   * bufferForDecompression size MUST be enough the decompressed data to fit
   * TODO RC: what to do if bufferForDecompression is smaller -- throw exception, or return actual bytes decompressed?
   */
  void decompress(@NotNull ByteBuffer bufferWithCompressedData,
                  byte[] bufferForDecompression) throws IOException;

  class ZipAlgo implements CompressingAlgo {

    /**
     * Compresses content if > compressContentLargerThan.
     * There is usually no reason to compress small content, but large content compression could
     * win a lot in both disk/memory space, and IO time.
     */
    private final int compressContentLargerThan;

    public ZipAlgo(int compressContentLargerThan) {
      this.compressContentLargerThan = compressContentLargerThan;
    }

    @Override
    public int algoID() {
      return ZIP_ID;
    }

    @Override
    public boolean shouldCompress(@NotNull ByteArraySequence contentBytes) {
      return contentBytes.length() > compressContentLargerThan;
    }

    //MAYBE RC: use thread-local Inflater/Deflater instances?

    @Override
    public ByteArraySequence compress(@NotNull ByteArraySequence input) throws IOException {
      Deflater deflater = new Deflater();
      try {
        deflater.setInput(input.getInternalBuffer(), input.getOffset(), input.length());
        deflater.finish();
        UnsyncByteArrayOutputStream compressedBytesStream = new UnsyncByteArrayOutputStream(input.length() / 2);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
          int bytesDeflated = deflater.deflate(buffer);
          compressedBytesStream.write(buffer, 0, bytesDeflated);
        }
        return compressedBytesStream.toByteArraySequence();
      }
      finally {
        deflater.end();
      }
    }

    @Override
    public void decompress(@NotNull ByteBuffer bufferWithCompressedData,
                           byte[] bufferForDecompression) throws IOException {
      int contentSize = bufferWithCompressedData.remaining();
      Inflater inflater = new Inflater();
      try {
        inflater.setInput(bufferWithCompressedData);
        int bytesInflated = inflater.inflate(bufferForDecompression);
        if (bytesInflated != bufferForDecompression.length) {
          throw new IOException("Decompressed bytes[" + bytesInflated + "b out of " + contentSize + "b] " +
                                "!= compressed bytes[" + bufferForDecompression.length + "] " +
                                "=> storage is likely corrupted"
          );
        }
        if (!inflater.finished()) {
          throw new IOException("Decompressed bytes[" + bytesInflated + "b out of " + contentSize + "b] " +
                                "but compressed stream is not finished yet " +
                                "=> storage is likely corrupted"
          );
        }
      }
      catch (DataFormatException e) {
        throw new IOException("Decompression [" + contentSize + "b] was failed => storage is likely corrupted", e);
      }
      finally {
        inflater.end();
      }
    }

    @Override
    public String toString() {
      return "ZIP[ > " + compressContentLargerThan + "b ]";
    }
  }

  class Lz4Algo implements CompressingAlgo {

    /**
     * Compresses content if > compressContentLargerThan.
     * There is usually no reason to compress small content, but large content compression could
     * win a lot in both disk/memory space, and IO time.
     */
    private final int compressContentLargerThan;

    public Lz4Algo(int compressContentLargerThan) {
      this.compressContentLargerThan = compressContentLargerThan;
    }

    @Override
    public int algoID() {
      return LZ4_ID;
    }

    @Override
    public boolean shouldCompress(@NotNull ByteArraySequence contentBytes) {
      return contentBytes.length() > compressContentLargerThan;
    }

    //TODO RC: use compressor/decompressor instances from CompressionUtil

    @Override
    public ByteArraySequence compress(@NotNull ByteArraySequence input) throws IOException {
      try {
        int compressedLengthMax = LZ4Compressor.INSTANCE.maxCompressedLength(input.length());
        byte[] compressedBytes = new byte[compressedLengthMax];
        int actualCompressedLength = LZ4Compressor.INSTANCE.compress(
          input.getInternalBuffer(), input.getOffset(), input.length(),
          compressedBytes, 0, compressedBytes.length
        );
        //MAYBE RC: use ThreadLocalCachedByteArray to avoid allocation of large short-lived buffers?
        return new ByteArraySequence(compressedBytes, 0, actualCompressedLength);
      }
      catch (LZ4Exception e) {
        throw new IOException("Compressing " + input.length() + " bytes failed", e);
      }
    }

    @Override
    public void decompress(@NotNull ByteBuffer bufferWithCompressedData,
                           byte[] bufferForDecompression) throws IOException {
      int compressedSize = bufferWithCompressedData.remaining();

      //There is a .decompress(ByteBuffer,ByteBuffer) method in LZ4Decompressor API -- allows to avoid ByteBuffer to byte[]
      // copying below -- but this method is not implemented for direct 'source' ByteBuffer in our 'fast' LZ4Decompressor
      // (see lz4.kt)

      //MAYBE RC: use ThreadLocalCachedByteArray to avoid allocation of large short-lived buffers?
      byte[] compressedBytes = new byte[compressedSize];
      bufferWithCompressedData.get(compressedBytes);

      try {
        int decompressedSize = LZ4Decompressor.INSTANCE.decompress(compressedBytes, bufferForDecompression);

        if (decompressedSize != compressedSize) {
          throw new IOException("Decompressed bytes[" + decompressedSize + "b out of " + compressedSize + "b] " +
                                "!= compressed bytes[" + bufferForDecompression.length + "] " +
                                "=> storage is likely corrupted"
          );
        }
      }
      catch (LZ4Exception e) {
        throw new IOException("Decompressing " + compressedSize + " bytes into " + bufferForDecompression.length + " bytes failed", e);
      }
    }

    @Override
    public String toString() {
      return "LZ4[ > " + compressContentLargerThan + "b ]";
    }
  }

  /**
   * {@link #shouldCompress(ByteArraySequence)} returns false always,
   * compress/decompress methods throws {@link UnsupportedOperationException}
   */
  class NoCompressionAlgo implements CompressingAlgo {
    @Override
    public boolean shouldCompress(@NotNull ByteArraySequence input) {
      return false;
    }

    @Override
    public int algoID() {
      return NONE_ID;
    }

    @Override
    public ByteArraySequence compress(@NotNull ByteArraySequence input) throws IOException {
      throw new UnsupportedOperationException("Method should not be called since shouldCompress() returns false");
    }

    @Override
    public void decompress(@NotNull ByteBuffer bufferWithCompressedData,
                           byte[] bufferForDecompression) throws IOException {
      throw new UnsupportedOperationException("Method should not be called since shouldCompress() returns false");
    }

    @Override
    public String toString() {
      return "NONE";
    }
  }
}


