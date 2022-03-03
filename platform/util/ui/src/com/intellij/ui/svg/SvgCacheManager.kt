// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
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

private fun getLogger() = Logger.getInstance(SvgCacheManager::class.java)

@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private val store: MVStore
  private val scaleToMap: MutableMap<Float, MVMap<ByteArray, ImageValue>> = ConcurrentHashMap(2, 0.75f, 2)
  private val mapBuilder: MVMap.Builder<ByteArray, ImageValue>

  companion object {
    fun <K, V> getMap(scale: Float,
                      isDark: Boolean,
                      scaleToMap: MutableMap<Float, MVMap<K, V>>,
                      store: MVStore,
                      mapBuilder: MVMap.MapBuilder<MVMap<K, V>, K, V>): MVMap<K, V> {
      return scaleToMap.computeIfAbsent(scale + if (isDark) 10000 else 0) {
        store.openMap("icons@" + scale + if (isDark) "_d" else "", mapBuilder)
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
    store = storeBuilder.openOrNewOnIoError(dbFile, true) { getLogger().debug("Cannot open icon cache database", it) }
    storeErrorHandler.isStoreOpened = true
    val mapBuilder = MVMap.Builder<ByteArray, ImageValue>()
    mapBuilder.keyType(FixedByteArrayDataType(IMAGE_KEY_SIZE))
    mapBuilder.valueType(ImageValue.ImageValueSerializer())
    this.mapBuilder = mapBuilder
  }

  private class StoreErrorHandler : BiConsumer<Throwable, MVStore> {
    var isStoreOpened = false

    override fun accept(e: Throwable, store: MVStore) {
      val logger = getLogger()
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

  fun loadFromCache(themeDigest: ByteArray,
                    imageBytes: ByteArray,
                    scale: Float,
                    isDark: Boolean,
                    docSize: ImageLoader.Dimension2DDouble): Image? {
    val key = getCacheKey(themeDigest, imageBytes)
    val map = getMap(scale, isDark, scaleToMap, store, mapBuilder)
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val data = map.get(key) ?: return null
      val image = readImage(data)
      docSize.setSize((data.w / scale).toDouble(), (data.h / scale).toDouble())
      IconLoadMeasurer.svgCacheRead.end(start)
      return image
    }
    catch (e: Throwable) {
      getLogger().error(e)
      try {
        map.remove(key)
      }
      catch (e1: Exception) {
        getLogger().error("Cannot remove invalid entry", e1)
      }
      return null
    }
  }

  fun storeLoadedImage(themeDigest: ByteArray, imageBytes: ByteArray, scale: Float, image: BufferedImage) {
    val key = getCacheKey(themeDigest, imageBytes)
    getMap(scale, false, scaleToMap, store, mapBuilder).put(key, writeImage(image))
  }
}

private val ZERO_POINT = Point(0, 0)

private fun getCacheKey(themeDigest: ByteArray, imageBytes: ByteArray): ByteArray {
  val contentDigest = Xxh3.hashLongs(longArrayOf(
    Xxh3.hash(imageBytes), Xxh3.hash(themeDigest)))

  val buffer = ByteBuffer.allocate(IMAGE_KEY_SIZE)
  // add content size to key to reduce chance of hash collision (write as medium int)
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