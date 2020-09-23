/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.UsefulTestCase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.LongBitPacker;
import org.jetbrains.mvstore.type.DataType;
import org.jetbrains.mvstore.type.FixedByteArrayDataType;
import org.jetbrains.mvstore.type.KeyableDataType;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

public class MVStoreSlowTest {
  // test using a regular FS as in production, not using InMemoryFsRule
  @Rule
  public final TemporaryDirectory tempDir = new TemporaryDirectory();

  @Test
  public void largeData() throws IOException {
    doTest(0);
  }

  @Test
  public void largeDataCompressed() throws IOException {
    doTest(1);
  }

  @Test
  public void largeDataByteArrayCompressed() throws IOException {
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);

    Path file = tempDir.newPath();
    //Path file = Paths.get("/Volumes/data/test.db");
    Files.deleteIfExists(file);
    //int size = 100_000;
    int size = 80;

    Random random = new Random(1234);
    byte[][] keys = new byte[size][];
    for (int i = 0; i < size; i++) {
      keys[i] = new byte[12];
      random.nextBytes(keys[i]);
    }

    ImageValue[] values = generateValues(size);

    MVMap.Builder<byte[], ImageValue> mapBuilder = new MVMap.Builder<byte[], ImageValue>()
      .keyType(new FixedByteArrayDataType(12))
      .valueType(new ImageValue.ImageValueSerializer());

    MVStore.Builder storeBuilder = new MVStore.Builder().compressionLevel(1);
    try (MVStore store = storeBuilder.open(file)) {
      MVMap<byte[], ImageValue> map = store.openMap("icons", mapBuilder);

      for (int i = 0, l = keys.length; i < l; i++) {
        // for non-identity maps with object keys we use a distinct set of keys (the different object with the same value is used for successful “get” calls).
        byte[] key = keys[i];
        //ImageKey newKey = new ImageKey(key.contentDigest, key.contentLength);
        //if (i % oneFailureOutOf == 0) {
        //  newKey.contentDigest = i;
        //}
        map.put(key, values[i]);
      }

      store.commit();
    }

    // open again to verify and remove outdated chunks
    try (MVStore store = storeBuilder.open(file)) {
      MVMap<byte[], ImageValue> map = store.openMap("icons", mapBuilder);
      store.compactFile(1_000);
      map.size();
    }
  }

  private static void doTest(int compressionLevel) throws IOException {
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);

    //Path file = tempDir.newPath();
    Path file = Paths.get("/Volumes/data/test.db");
    Files.deleteIfExists(file);
    int mapSize = 100_000;
    ImageKey[] keys = loadObjectArray(mapSize);
    ImageValue[] values = generateValues(mapSize);

    MVMap.Builder<ImageKey, ImageValue> mapBuilder = new MVMap.Builder<ImageKey, ImageValue>()
      .keyType(new ImageKey.ImageKeySerializer())
      .valueType(new ImageValue.ImageValueSerializer());

    MVStore.Builder storeBuilder = new MVStore.Builder().compressionLevel(compressionLevel);
    try (MVStore store = storeBuilder.open(file)) {
      MVMap<ImageKey, ImageValue> map = store.openMap("icons", mapBuilder);

      int oneFailureOutOf = 2;
      for (int i = 0, l = keys.length; i < l; i++) {
        // for non-identity maps with object keys we use a distinct set of keys (the different object with the same value is used for successful “get” calls).
        ImageKey key = keys[i];
        ImageKey
          newKey = new ImageKey(key.contentDigest, key.contentLength);
        if (i % oneFailureOutOf == 0) {
          newKey.contentDigest = i;
        }
        map.put(newKey, values[i]);
      }

      store.commit();
    }

    // open again to verify and remove outdated chunks
    try (MVStore store = storeBuilder.open(file)) {
      MVMap<ImageKey, ImageValue> map = store.openMap("icons", mapBuilder);
      store.compactFile(1_000);
      map.size();
    }
  }

  private static ImageKey[] loadObjectArray(int size) {
    Random random = new Random(1234);
    ImageKey[] result = new ImageKey[size];
    for (int i = 0; i < size; i++) {
      result[i] = new ImageKey(random.nextLong(), random.nextInt());
    }
    return result;
  }

  private static ImageValue[] generateValues(@SuppressWarnings("SameParameterValue") int size) {
    Random random = new Random(1234);
    ImageValue[] result = new ImageValue[size];
    int maxDataSize = 20 * 1024;
    int minDataSize = 2 * 1024;
    for (int i = 0; i < size; i++) {
      byte[] data = new byte[random.nextInt(maxDataSize - minDataSize) + minDataSize];
      random.nextBytes(data);
      result[i] = new ImageValue(data, random.nextFloat(), random.nextFloat(), random.nextInt(), random.nextInt());
    }
    return result;
  }
}

final class ImageKey implements Comparable<ImageKey> {
  long contentDigest;
  // add image size to prevent collision
  final int contentLength;

  ImageKey(long contentDigest, int contentLength) {
    this.contentDigest = contentDigest;
    this.contentLength = contentLength;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ImageKey key = (ImageKey)o;
    return contentDigest == key.contentDigest && contentLength == key.contentLength;
  }

  @Override
  public int hashCode() {
    int result = (int)(contentDigest ^ (contentDigest >>> 32));
    result = 31 * result + contentLength;
    return result;
  }

  @Override
  public int compareTo(@NotNull ImageKey o) {
    if (contentDigest != o.contentDigest) {
      return contentDigest < o.contentDigest ? -1 : 1;
    }
    return Integer.compare(contentLength, o.contentLength);
  }

  static final class ImageKeySerializer implements KeyableDataType<ImageKey> {
    @Override
    public int compare(ImageKey a, ImageKey b) {
      return a.compareTo(b);
    }

    @Override
    public int getMemory(ImageKey obj) {
      return getFixedMemory();
    }

    @Override
    public int getFixedMemory() {
      return DataUtil.VAR_LONG_MAX_SIZE + DataUtil.VAR_INT_MAX_SIZE;
    }

    @Override
    public void write(ByteBuf buf, ImageKey obj) {
      LongBitPacker.writeVar(buf, obj.contentDigest);
      IntBitPacker.writeVar(buf, obj.contentLength);
    }

    @Override
    public ImageKey read(ByteBuf buff) {
      return new ImageKey(LongBitPacker.readVar(buff), IntBitPacker.readVar(buff));
    }

    @Override
    public ImageKey[] createStorage(int size) {
      return new ImageKey[size];
    }
  }
}

final class ImageValue {
  private final byte[] data;
  private final float width;
  private final float height;
  private final int actualWidth;
  private final int actualHeight;

  ImageValue(byte[] data, float width, float height, int actualWidth, int actualHeight) {
    this.data = data;
    this.width = width;
    this.height = height;
    this.actualWidth = actualWidth;
    this.actualHeight = actualHeight;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ImageValue value = (ImageValue)o;
    return Float.compare(value.width, width) == 0 &&
           Float.compare(value.height, height) == 0 &&
           actualWidth == value.actualWidth &&
           actualHeight == value.actualHeight && Arrays.equals(data, value.data);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(data);
    result = 31 * result + (width != +0.0f ? Float.floatToIntBits(width) : 0);
    result = 31 * result + (height != +0.0f ? Float.floatToIntBits(height) : 0);
    result = 31 * result + actualWidth;
    result = 31 * result + actualHeight;
    return result;
  }

  static final class ImageValueSerializer implements DataType<ImageValue> {
    private static final long MAX_IMAGE_SIZE = 16 * 1024 * 1024;

    @Override
    public ImageValue[] createStorage(int size) {
      return new ImageValue[size];
    }

    @Override
    public int getMemory(ImageValue obj) {
      return Float.BYTES * 2 +
             4 /* w or h var int size (strictly speaking 5, but 4 is totally ok) */ * 2 +
             DataUtil.VAR_INT_MAX_SIZE /* max var int size */ + obj.data.length;
    }

    @Override
    public int getFixedMemory() {
      return -1;
    }

    @Override
    public void write(ByteBuf buf, ImageValue obj) {
      buf.writeFloat(obj.width);
      buf.writeFloat(obj.height);
      IntBitPacker.writeVar(buf, obj.actualWidth);
      IntBitPacker.writeVar(buf, obj.actualHeight);

      DataUtil.writeByteArray(buf, obj.data);
    }

    @Override
    public ImageValue read(ByteBuf buf) {
      float width = buf.readFloat();
      float height = buf.readFloat();
      int actualWidth = IntBitPacker.readVar(buf);
      int actualHeight = IntBitPacker.readVar(buf);

      int length = IntBitPacker.readVar(buf);
      if (length > MAX_IMAGE_SIZE) {
        throw new IllegalStateException("Size of data is too big: " + length);
      }

      byte[] obj = ByteBufUtil.getBytes(buf, buf.readerIndex(), length);
      buf.readerIndex(buf.readerIndex() + length);
      return new ImageValue(obj, width, height, actualWidth, actualHeight);
    }
  }
}