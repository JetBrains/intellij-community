// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.util.ImageLoader;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.MVStore;
import org.jetbrains.mvstore.type.FixedByteArrayDataType;

import java.awt.*;
import java.awt.image.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@SuppressWarnings("UndesirableClassUsage")
@ApiStatus.Internal
public final class SvgCacheManager {
  private static final long HASH_SEED = 0x9747b28c;
  private static final int IMAGE_KEY_SIZE = Long.BYTES + 3;

  private final MVStore store;
  private final Map<Float, MVMap<byte[], ImageValue>> scaleToMap = new ConcurrentHashMap<>(2, 0.75f, 2);
  private final MVMap.Builder<byte[], ImageValue> mapBuilder;

  private static final @Nullable MethodHandle getDataHandle = getGetDataHandle();

  private static MethodHandle getGetDataHandle() {
    try {
      return MethodHandles.privateLookupIn(DataBufferInt.class, MethodHandles.lookup())
        .findGetter(DataBufferInt.class, "data", int[].class);
    }
    catch (Throwable e) {
      getLogger().error("cannot create DataBufferByte.data accessor", e);
      return null;
    }
  }

  private static final class StoreErrorHandler implements BiConsumer<Throwable, MVStore> {
    private boolean isStoreOpened;

    @Override
    public void accept(Throwable e, MVStore store) {
      Logger logger = getLogger();
      if (isStoreOpened) {
        logger.error("Icon cache error (db=" + store.getFileStore() + ')');
      }
      else {
        logger.warn("Icon cache will be recreated or previous version of data reused, (db=" + store.getFileStore() + ')');
      }
      logger.debug(e);
    }
  }

  public SvgCacheManager(@NotNull Path dbFile) {
    StoreErrorHandler storeErrorHandler = new StoreErrorHandler();
    MVStore.Builder storeBuilder = new MVStore.Builder()
      .backgroundExceptionHandler(storeErrorHandler)
      .autoCommitDelay(60_000)
      .compressionLevel(1);
    store = storeBuilder.openOrNewOnIoError(dbFile, true, e -> {
      getLogger().debug("Cannot open icon cache database", e);
    });
    storeErrorHandler.isStoreOpened = true;

    MVMap.Builder<byte[], ImageValue> mapBuilder;
    mapBuilder = new MVMap.Builder<>();
    mapBuilder.keyType(new FixedByteArrayDataType(IMAGE_KEY_SIZE));
    mapBuilder.valueType(new ImageValue.ImageValueSerializer());
    this.mapBuilder = mapBuilder;
  }

  static @NotNull Logger getLogger() {
    return Logger.getInstance(SvgCacheManager.class);
  }

  public static <K, V> @NotNull MVMap<K, V> getMap(float scale,
                                                   boolean isDark,
                                                   @NotNull Map<Float, MVMap<K, V>> scaleToMap,
                                                   @NotNull MVStore store,
                                                   @NotNull MVMap.MapBuilder<MVMap<K, V>, K, V> mapBuilder) {
    return scaleToMap.computeIfAbsent(scale + (isDark ? 10_000 : 0), __ -> {
      return store.openMap("icons-v1@" + scale + (isDark ? "_d" : ""), mapBuilder);
    });
  }

  public void close() {
    store.close();
  }

  public void save() {
    store.triggerAutoSave();
  }

  private static byte[] getCacheKey(byte @NotNull [] themeDigest, byte @NotNull [] imageBytes) {
    XXHashFactory hashFactory = XXHashFactory.fastestJavaInstance();
    long contentDigest;
    if (themeDigest.length == 0) {
      contentDigest = hashFactory.hash64().hash(imageBytes, 0, imageBytes.length, HASH_SEED);
    }
    else {
      StreamingXXHash64 hasher = hashFactory.newStreamingHash64(HASH_SEED);
      // hash content to ensure that value is not reused if outdated
      hasher.update(themeDigest, 0, themeDigest.length);
      hasher.update(imageBytes, 0, imageBytes.length);
      contentDigest = hasher.getValue();
      hasher.close();
    }

    ByteBuffer buffer = ByteBuffer.allocate(IMAGE_KEY_SIZE);
    // add content size to key to reduce chance of hash collision (write as medium int)
    buffer.put((byte)(imageBytes.length >>> 16));
    buffer.put((byte)(imageBytes.length >>> 8));
    buffer.put((byte)imageBytes.length);

    buffer.putLong(contentDigest);
    return buffer.array();
  }

  public @Nullable Image loadFromCache(byte @NotNull [] themeDigest,
                                       byte @NotNull [] imageBytes,
                                       float scale,
                                       boolean isDark,
                                       @NotNull ImageLoader.Dimension2DDouble docSize) {
    byte[] key = getCacheKey(themeDigest, imageBytes);
    MVMap<byte[], ImageValue> map = getMap(scale, isDark, scaleToMap, store, mapBuilder);
    try {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();

      ImageValue data = map.get(key);
      if (data == null) {
        return null;
      }

      Image image = readImage(data);
      docSize.setSize((data.w / scale), (data.h / scale));
      IconLoadMeasurer.svgCacheRead.end(start);
      return image;
    }
    catch (Throwable e) {
      getLogger().error(e);
      try {
        map.remove(key);
      }
      catch (Exception e1) {
        getLogger().error("Cannot remove invalid entry", e1);
      }
      return null;
    }
  }

  public void storeLoadedImage(byte @NotNull [] themeDigest,
                               byte @NotNull [] imageBytes,
                               float scale,
                               @NotNull BufferedImage image) {
    byte[] key = getCacheKey(themeDigest, imageBytes);
    getMap(scale, false, scaleToMap, store, mapBuilder).put(key, writeImage(image));
  }

  static @NotNull Image readImage(@NotNull ImageValue value) throws Throwable {
    // Create a STABLE internal buffer. It will be marked dirty for now, but will remain STABLE after a refresh.
    DataBufferInt dataBuffer = new DataBufferInt(value.w * value.h);

    if (getDataHandle == null) {
      for (int i = 0; i < value.data.length; i++) {
        dataBuffer.setElem(i, value.data[i]);
      }
    }
    else {
      int[] ints = (int[])getDataHandle.invokeExact(dataBuffer);
      System.arraycopy(value.data, 0, ints, 0, value.data.length);
    }
    return createImage(value.w, value.h, dataBuffer);
  }

  private static final Point ZERO_POINT = new Point(0, 0);

  static Image readImage(@NotNull ByteBuffer buffer, int w, int h) throws Throwable {
    // Create a STABLE internal buffer. It will be marked dirty for now, but will remain STABLE after a refresh.
    int size = w * h;
    DataBufferInt dataBuffer = new DataBufferInt(size);

    // critical for a proper colors, see https://en.wikipedia.org/wiki/RGBA_color_model
    // On little-endian systems, this is equivalent to BGRA byte order. On big-endian systems, this is equivalent to ARGB byte order.
    buffer.order(ByteOrder.BIG_ENDIAN);

    if (getDataHandle == null) {
      IntBuffer intBuffer = buffer.asIntBuffer();
      for (int i = 0; intBuffer.hasRemaining(); i++) {
        dataBuffer.setElem(i, intBuffer.get());
      }
    }
    else {
      buffer.asIntBuffer().get((int[])getDataHandle.invokeExact(dataBuffer));
    }
    return createImage(w, h, dataBuffer);
  }

  private static @NotNull BufferedImage createImage(int w, int h, DataBufferInt dataBuffer) {
    DirectColorModel colorModel = (DirectColorModel)ColorModel.getRGBdefault();
    WritableRaster raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.getMasks(), ZERO_POINT);
    return new BufferedImage(colorModel, raster, false, null);
  }

  private static @NotNull ImageValue writeImage(@NotNull BufferedImage image) {
    int w = image.getWidth();
    int h = image.getHeight();

    BufferedImage convertedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = convertedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();

    DataBufferInt dataBufferInt = (DataBufferInt)convertedImage.getRaster().getDataBuffer();
    return new ImageValue(dataBufferInt.getData(), w, h);
  }
}
