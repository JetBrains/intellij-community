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
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@SuppressWarnings("UndesirableClassUsage")
@ApiStatus.Internal
public final class SvgCacheManager {
  private static final long HASH_SEED = 0x9747b28c;
  private static final int[] B_OFFS = new int[]{3, 2, 1, 0};
  private static final int IMAGE_KEY_SIZE = Long.BYTES + 3;

  private static final ComponentColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[]{8, 8, 8, 8}, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

  private final MVStore store;
  private final Map<Float, MVMap<byte[], ImageValue>> scaleToMap = new ConcurrentHashMap<>(2, 0.75f, 2);
  private final MVMap.Builder<byte[], ImageValue> mapBuilder;

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

      Image image = readImage(data, docSize);
      IconLoadMeasurer.svgCacheRead.end(start);
      return image;
    }
    catch (Exception e) {
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
                               @NotNull BufferedImage image,
                               @NotNull ImageLoader.Dimension2DDouble size) {
    byte[] key = getCacheKey(themeDigest, imageBytes);
    getMap(scale, false, scaleToMap, store, mapBuilder).put(key, writeImage(image, size));
  }

  static @Nullable Image readImage(@NotNull ImageValue value, @NotNull ImageLoader.Dimension2DDouble docSize) {
    // sanity check to make sure file is not corrupted
    if (value.actualWidth <= 0 || value.actualHeight <= 0 || value.actualWidth * value.actualHeight <= 0) {
      return null;
    }

    DataBuffer dataBuffer = createBuffer(value.data, value.actualWidth * 4 * (value.actualHeight - 1) + 4 * value.actualWidth);
    WritableRaster raster = Raster.createInterleavedRaster(dataBuffer, value.actualWidth, value.actualHeight, value.actualWidth * 4, 4, B_OFFS, null);
    Image image = new BufferedImage(colorModel, raster, false, null);
    docSize.setSize(value.width, value.height);
    return image;
  }

  public static @NotNull ImageValue writeImage(@NotNull BufferedImage image, @NotNull ImageLoader.Dimension2DDouble size) {
    int actualWidth = image.getWidth();
    int actualHeight = image.getHeight();

    BufferedImage convertedImage = new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_4BYTE_ABGR);

    Graphics2D g = convertedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();

    byte[] imageData = extractBufferData((DataBufferByte)convertedImage.getRaster().getDataBuffer());
    return new ImageValue(imageData, (float)size.getWidth(), (float)size.getHeight(), actualWidth, actualHeight);
  }

  private static DataBuffer createBuffer(byte[] data, int size) {
    // Create a STABLE internal buffer. It will be marked dirty for now, but will remain STABLE after a refresh.
    DataBufferByte dataBuffer = new DataBufferByte(size);
    for (int i = 0; i < data.length; i++) {
      dataBuffer.setElem(i, data[i]);
    }
    return dataBuffer;
  }

  private static byte[] extractBufferData(DataBufferByte dataBufferByte) {
    // Calling getData() directly will mark the internal buffer UNTRACKABLE, so preserve ownership.
    byte[] imageData = new byte[dataBufferByte.getSize()];
    for (int i = 0; i < imageData.length; i++) {
      imageData[i] = (byte)dataBufferByte.getElem(i);
    }
    return imageData;
  }
}
