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
public final class Ikv implements AutoCloseable {
  private final RecSplitEvaluator<Long> evaluator;
  private final long[] offsetAndSizePairs;
  private ByteBuffer mappedBuffer;

  private Ikv(RecSplitEvaluator<Long> evaluator, long[] offsetAndSizePairs, ByteBuffer mappedBuffer) {
    this.evaluator = evaluator;
    this.offsetAndSizePairs = offsetAndSizePairs;
    this.mappedBuffer = mappedBuffer;
  }

  public static @NotNull Ikv loadIkv(@NotNull Path file) throws IOException {
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

    int position = fileSize - Integer.BYTES;
    int keyDataSize = mappedBuffer.getInt(position);
    position -= Integer.BYTES;
    int entryCount = mappedBuffer.getInt(position);

    int offsetAndSizePairsDataSize = entryCount * Long.BYTES;

    // read key data
    position -= keyDataSize + offsetAndSizePairsDataSize;
    mappedBuffer.position(position);
    BitBuffer keyData = BitBuffer.readFrom(mappedBuffer, keyDataSize);

    // read value offsets
    position += keyDataSize;
    mappedBuffer.position(position);
    long[] valueOffsets = new long[entryCount];
    mappedBuffer.asLongBuffer().get(valueOffsets);

    return new Ikv(new RecSplitEvaluator<>(keyData, new LongHash(), new Settings(10, 256)), valueOffsets, mappedBuffer);
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
