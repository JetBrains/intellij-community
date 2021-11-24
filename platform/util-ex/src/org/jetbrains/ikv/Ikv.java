// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv;

import org.jetbrains.annotations.NotNull;
import org.minperf.BitBuffer;
import org.minperf.RecSplitEvaluator;
import org.minperf.Settings;
import org.minperf.universal.LongHash;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
public abstract class Ikv implements AutoCloseable {
  private static final LongHash LONG_HASH = new LongHash();

  protected final RecSplitEvaluator<Long> evaluator;
  protected ByteBuffer mappedBuffer;

  public static final class SizeAwareIkv extends Ikv {
    private final long[] offsetAndSizePairs;

    private SizeAwareIkv(RecSplitEvaluator<Long> evaluator, ByteBuffer mappedBuffer, long[] offsetAndSizePairs) {
      super(evaluator, mappedBuffer);

      this.offsetAndSizePairs = offsetAndSizePairs;
    }

    public ByteBuffer getValue(int key) {
      int index = evaluator.evaluate((long)key);
      if (index < 0) {
        return null;
      }

      long pair = offsetAndSizePairs[index];
      int start = (int)(pair >> 32);
      ByteBuffer buffer = mappedBuffer.asReadOnlyBuffer();
      buffer.position(start);
      buffer.limit(start + (int)pair);
      return buffer;
    }
  }

  public static final class SizeUnawareIkv extends Ikv {
    private final int[] offsets;

    private SizeUnawareIkv(RecSplitEvaluator<Long> evaluator, ByteBuffer mappedBuffer, int[] offsets) {
      super(evaluator, mappedBuffer);

      this.offsets = offsets;
    }

    // if size is known by reader
    public ByteBuffer getUnboundedValue(int key) {
      int index = evaluator.evaluate((long)key);
      if (index < 0) {
        return null;
      }
      return mappedBuffer.asReadOnlyBuffer().position(offsets[index]);
    }
  }

  private Ikv(RecSplitEvaluator<Long> evaluator, ByteBuffer mappedBuffer) {
    this.evaluator = evaluator;
    this.mappedBuffer = mappedBuffer;
  }

  public static @NotNull Ikv.SizeAwareIkv loadSizeAwareIkv(@NotNull Path file) throws IOException {
    return (SizeAwareIkv)doLoadIkv(file);
  }

  public static @NotNull SizeUnawareIkv loadSizeUnawareIkv(@NotNull Path file) throws IOException {
    return (SizeUnawareIkv)doLoadIkv(file);
  }

  public static @NotNull Ikv doLoadIkv(@NotNull Path file) throws IOException {
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
      mappedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    int position = fileSize - 1;
    boolean withSize = mappedBuffer.get(position) == 1;
    position -= Integer.BYTES;
    int keyDataSize = mappedBuffer.getInt(position);
    position -= Integer.BYTES;
    int entryCount = mappedBuffer.getInt(position);

    int offsetAndSizePairsDataSize = entryCount * (withSize ? Long.BYTES : Integer.BYTES);

    // read key data
    position -= keyDataSize + offsetAndSizePairsDataSize;
    mappedBuffer.position(position);
    BitBuffer keyData = BitBuffer.readFrom(mappedBuffer, keyDataSize);

    // read value offsets
    position += keyDataSize;
    Settings settings = new Settings(10, 256);
    mappedBuffer.position(position);
    if (withSize) {
      long[] offsetAndSizePairs = new long[entryCount];
      mappedBuffer.asLongBuffer().get(offsetAndSizePairs);
      return new Ikv.SizeAwareIkv(new RecSplitEvaluator<>(keyData, LONG_HASH, settings), mappedBuffer, offsetAndSizePairs);
    }
    else {
      int[] valueOffsets = new int[entryCount];
      mappedBuffer.asIntBuffer().get(valueOffsets);
      return new Ikv.SizeUnawareIkv(new RecSplitEvaluator<>(keyData, LONG_HASH, settings), mappedBuffer, valueOffsets);
    }
  }

  @Override
  public void close() throws Exception {
    ByteBuffer buffer = mappedBuffer;
    if (buffer == null) {
      return;
    }

    mappedBuffer = null;
    try {
      unmapBuffer(buffer);
    }
    catch (Exception e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static volatile MethodHandle cleaner;

  /**
   * This method repeats logic from {@link com.intellij.util.io.ByteBufferUtil#cleanBuffer} which isn't accessible from this module
   */
  static void unmapBuffer(@NotNull ByteBuffer buffer) throws Throwable {
    if (!buffer.isDirect()) {
      return;
    }

    MethodHandle cleaner = Ikv.cleaner;
    if (cleaner == null) {
      cleaner = getByteBufferCleaner();
    }
    cleaner.invokeExact(buffer);
  }

  private synchronized static @NotNull MethodHandle getByteBufferCleaner() throws Throwable {
    MethodHandle cleaner = Ikv.cleaner;
    if (cleaner != null) {
      return cleaner;
    }

    Class<?> unsafeClass = ClassLoader.getPlatformClassLoader().loadClass("sun.misc.Unsafe");
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(unsafeClass, MethodHandles.lookup());
    Object unsafe = lookup.findStaticGetter(unsafeClass, "theUnsafe", unsafeClass).invoke();
    cleaner = lookup.findVirtual(unsafeClass, "invokeCleaner", MethodType.methodType(Void.TYPE, ByteBuffer.class)).bindTo(unsafe);
    Ikv.cleaner = cleaner;
    return cleaner;
  }
}
