// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv;

import com.intellij.util.lang.ByteBufferCleaner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/*
byte array - values
key metadata - keys (sorted by index (probe provides unique index because we check collisions))
long array - value offset and size pairs
int - key data size
int - entry count

Value offsets are not sequential because the array is sorted by MPH. But values are written in a user order.
So, besides offset, we have to store size. Values are not sorted by MPH to ensure that we will map the file to memory efficiently
 (assume that the user's order groups related values together).

Little endian is used because both Intel and M1 CPU uses little endian (saves a little for writing and reading integers).
*/
@ApiStatus.Internal
public abstract class Ikv<T> implements AutoCloseable {
  public final RecSplitEvaluator<T> evaluator;
  protected ByteBuffer mappedBuffer;

  public ByteBuffer getMappedBufferAt(int position) {
    return mappedBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).position(position);
  }

  public static final class SizeAwareIkv<T> extends Ikv<T> {
    private final long[] offsetAndSizePairs;

    private SizeAwareIkv(RecSplitEvaluator<T> evaluator, ByteBuffer mappedBuffer, long[] offsetAndSizePairs) {
      super(evaluator, mappedBuffer);

      this.offsetAndSizePairs = offsetAndSizePairs;
    }

    public ByteBuffer getValue(T key) {
      int index = evaluator.evaluate(key);
      return index < 0 ? null : getByteBufferAt(index);
    }

    public int getIndex(T key) {
      return evaluator.evaluate(key);
    }

    public int getSizeAt(int index) {
      return (int)offsetAndSizePairs[index];
    }

    public ByteBuffer getByteBufferAt(int index) {
      long pair = offsetAndSizePairs[index];
      int start = (int)(pair >> 32);
      ByteBuffer buffer = mappedBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
      buffer.position(start);
      buffer.limit(start + (int)pair);
      return buffer;
    }

    public byte @NotNull [] getByteArrayAt(int index) {
      ByteBuffer buffer = getByteBufferAt(index);
      byte[] result = new byte[buffer.remaining()];
      buffer.get(result);
      return result;
    }
  }

  public static final class SizeUnawareIkv<T> extends Ikv<T> {
    private final int[] offsets;

    private SizeUnawareIkv(RecSplitEvaluator<T> evaluator, ByteBuffer mappedBuffer, int[] offsets) {
      super(evaluator, mappedBuffer);

      this.offsets = offsets;
    }

    // if size is known by reader
    public ByteBuffer getUnboundedValue(T key) {
      int index = evaluator.evaluate(key);
      if (index < 0) {
        return null;
      }
      return mappedBuffer.asReadOnlyBuffer().position(offsets[index]).order(ByteOrder.LITTLE_ENDIAN);
    }
  }

  private Ikv(RecSplitEvaluator<T> evaluator, ByteBuffer mappedBuffer) {
    this.evaluator = evaluator;
    this.mappedBuffer = mappedBuffer;
  }

  public static @NotNull <T> Ikv.SizeAwareIkv<T> loadSizeAwareIkv(@NotNull Path file, UniversalHash<T> hash) throws IOException {
    return (SizeAwareIkv<T>)doLoadIkv(file, hash, RecSplitSettings.DEFAULT_SETTINGS);
  }

  public static @NotNull <T> SizeUnawareIkv<T> loadSizeUnawareIkv(@NotNull Path file, UniversalHash<T> hash) throws IOException {
    return (SizeUnawareIkv<T>)doLoadIkv(file, hash, RecSplitSettings.DEFAULT_SETTINGS);
  }

  public static <T> @NotNull Ikv<T> doLoadIkv(@NotNull Path file, UniversalHash<T> hash, RecSplitSettings settings) throws IOException {
    ByteBuffer mappedBuffer;
    int fileSize;
    try (FileChannel fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ))) {
      fileSize = (int)fileChannel.size();
      try {
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
      }
      catch (UnsupportedOperationException e) {
        // in memory fs
        mappedBuffer = ByteBuffer.allocate(fileSize);
        while (mappedBuffer.hasRemaining()) {
          fileChannel.read(mappedBuffer);
        }
        mappedBuffer.rewind();
      }
    }
    mappedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    mappedBuffer.position(fileSize);
    return loadIkv(mappedBuffer, hash, settings);
  }

  public static <T> @NotNull Ikv<T> loadIkv(@NotNull ByteBuffer mappedBuffer,
                                            @NotNull UniversalHash<T> hash,
                                            @NotNull RecSplitSettings settings) {
    int position = mappedBuffer.position() - Byte.BYTES - (Integer.BYTES * 2);
    mappedBuffer.position(position);

    int entryCount = mappedBuffer.getInt();
    int keyDataSize = mappedBuffer.getInt();
    boolean withSize = mappedBuffer.get() == 1;

    int offsetAndSizePairsDataSize = entryCount * (withSize ? Long.BYTES : Integer.BYTES);

    // read key data
    position -= keyDataSize + offsetAndSizePairsDataSize;
    mappedBuffer.position(position);
    ByteBuffer keyData = mappedBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    keyData.limit(position + keyDataSize);

    // read value offsets
    position += keyDataSize;
    mappedBuffer.position(position);
    if (withSize) {
      long[] offsetAndSizePairs = new long[entryCount];
      mappedBuffer.asLongBuffer().get(offsetAndSizePairs);
      return new Ikv.SizeAwareIkv<>(new RecSplitEvaluator<T>(keyData, hash, settings), mappedBuffer, offsetAndSizePairs);
    }
    else {
      int[] valueOffsets = new int[entryCount];
      mappedBuffer.asIntBuffer().get(valueOffsets);
      return new Ikv.SizeUnawareIkv<>(new RecSplitEvaluator<T>(keyData, hash, settings), mappedBuffer, valueOffsets);
    }
  }

  @Override
  public void close() throws Exception {
    ByteBuffer buffer = mappedBuffer;
    if (buffer != null) {
      mappedBuffer = null;
      ByteBufferCleaner.unmapBuffer(buffer);
    }
  }
}
