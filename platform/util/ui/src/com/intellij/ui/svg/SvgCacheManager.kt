// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.ImageLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.mvstore.MVMap
import org.jetbrains.mvstore.MVStore
import org.jetbrains.mvstore.type.FixedByteArrayDataType
import org.jetbrains.xxh3.Xxh3
import sun.awt.image.SunWritableRaster
import java.awt.Image
import java.awt.Point
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

private const val IMAGE_KEY_SIZE = java.lang.Long.BYTES + 3

private val LOG: Logger
  get() = logger<SvgCacheManager>()

@ApiStatus.Internal
class SvgCacheMapper(
  @JvmField internal val scale: Float,
  @JvmField internal val isDark: Boolean,
  @JvmField internal val isStroke: Boolean,
  @JvmField internal val docSize: ImageLoader.Dimension2DDouble? = null,
) {
  constructor(scale: Float) : this(scale = scale, isDark = false, isStroke = false)

  internal val key: Float
    get() = scale + (if (isDark) 10_000 else 0) + (if (isStroke) 100_000 else 0)

  internal val name: String
    get() = "icons@$scale${if (isDark) "_d" else ""}${if (isStroke) "_s" else ""}"
}

@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private val store: MVStore
  private val scaleToMap = ConcurrentHashMap<Float, MVMap<ByteArray, ImageValue>>(2, 0.75f, 2)
  private val mapBuilder: MVMap.Builder<ByteArray, ImageValue>

  companion object {
    fun <K, V> getMap(mapper: SvgCacheMapper,
                      scaleToMap: MutableMap<Float, MVMap<K, V>>,
                      store: MVStore,
                      mapBuilder: MVMap.MapBuilder<MVMap<K, V>, K, V>): MVMap<K, V> {
      return scaleToMap.computeIfAbsent(mapper.key) {
        store.openMap(mapper.name, mapBuilder)
      }
    }

    fun readImage(value: ImageValue): Image {
      val dataBuffer = DataBufferInt(value.data, value.data.size)
      SunWritableRaster.makeTrackable(dataBuffer)
      return createImage(value.w, value.h, dataBuffer)
    }

    fun readImage(buffer: ByteBuffer, w: Int, h: Int): Image {
      val dataBuffer = DataBufferInt(w * h)
      buffer.asIntBuffer().get(SunWritableRaster.stealData(dataBuffer, 0))
      SunWritableRaster.makeTrackable(dataBuffer)
      return createImage(w, h, dataBuffer)
    }
  }

  init {
    val storeErrorHandler = StoreErrorHandler()
    val storeBuilder = MVStore.Builder()
      .backgroundExceptionHandler(storeErrorHandler)
      .autoCommitDelay(60000)
      .compressionLevel(1)
    store = storeBuilder.openOrNewOnIoError(dbFile, true) { LOG.debug("Cannot open icon cache database", it) }
    storeErrorHandler.isStoreOpened = true
    val mapBuilder = MVMap.Builder<ByteArray, ImageValue>()
    mapBuilder.keyType(FixedByteArrayDataType(IMAGE_KEY_SIZE))
    mapBuilder.valueType(ImageValue.ImageValueSerializer())
    this.mapBuilder = mapBuilder
  }

  private class StoreErrorHandler : BiConsumer<Throwable, MVStore> {
    var isStoreOpened = false

    override fun accept(e: Throwable, store: MVStore) {
      val logger = LOG
      if (isStoreOpened) {
        logger.error("Icon cache error (db=$store)")
      }
      else {
        logger.warn("Icon cache will be recreated or previous version of data reused, (db=$store)")
      }
      logger.debug(e)
    }
  }

  fun close() {
    store.close()
  }

  fun save() {
    store.triggerAutoSave()
  }

  fun loadFromCache(themeDigest: ByteArray, imageBytes: ByteArray, mapper: SvgCacheMapper): Image? {
    val key = getCacheKey(themeDigest, imageBytes)
    val map = getMap(mapper, scaleToMap, store, mapBuilder)
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val data = map.get(key) ?: return null
      val image = readImage(data)
      mapper.docSize?.setSize((data.w / mapper.scale).toDouble(), (data.h / mapper.scale).toDouble())
      IconLoadMeasurer.svgCacheRead.end(start)
      return image
    }
    catch (e: Throwable) {
      LOG.error(e)
      try {
        map.remove(key)
      }
      catch (e1: Exception) {
        LOG.error("Cannot remove invalid entry", e1)
      }
      return null
    }
  }

  fun storeLoadedImage(themeDigest: ByteArray, imageBytes: ByteArray, mapper: SvgCacheMapper, image: BufferedImage) {
    val key = getCacheKey(themeDigest = themeDigest, imageBytes = imageBytes)
    getMap(mapper = mapper, scaleToMap = scaleToMap, store = store, mapBuilder = mapBuilder).put(key, writeImage(image))
  }
}

private val ZERO_POINT = Point(0, 0)

private fun getCacheKey(themeDigest: ByteArray, imageBytes: ByteArray): ByteArray {
  val contentDigest = Xxh3.hashLongs(longArrayOf(Xxh3.hash(imageBytes), Xxh3.hash(themeDigest)))

  val buffer = ByteBuffer.allocate(IMAGE_KEY_SIZE)
  // add content size to a key to reduce chance of hash collision (write as medium int)
  buffer.put((imageBytes.size ushr 16).toByte())
  buffer.put((imageBytes.size ushr 8).toByte())
  buffer.put(imageBytes.size.toByte())
  buffer.putLong(contentDigest)
  return buffer.array()
}

private fun createImage(w: Int, h: Int, dataBuffer: DataBufferInt): BufferedImage {
  val colorModel = ColorModel.getRGBdefault() as DirectColorModel
  val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, ZERO_POINT)
  @Suppress("UndesirableClassUsage")
  return BufferedImage(colorModel, raster, false, null)
}

private fun writeImage(image: BufferedImage): ImageValue {
  val w = image.width
  val h = image.height

  @Suppress("UndesirableClassUsage")
  val convertedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  val g = convertedImage.createGraphics()
  g.drawImage(image, 0, 0, null)
  g.dispose()
  val dataBufferInt = convertedImage.raster.dataBuffer as DataBufferInt
  return ImageValue(dataBufferInt.data, w, h)
}