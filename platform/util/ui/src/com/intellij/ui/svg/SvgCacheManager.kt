// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.mvstore.openOrRecreateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.IconLoadMeasurer
import org.h2.mvstore.DataUtils
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.BasicDataType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.xxh3.Xxh3
import sun.awt.image.SunWritableRaster
import java.awt.Image
import java.awt.Point
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val LOG: Logger
  get() = logger<SvgCacheManager>()

@ApiStatus.Internal
class SvgCacheMapper(
  @JvmField internal val scale: Float,
  @JvmField internal val isDark: Boolean,
  @JvmField internal val isStroke: Boolean,
) {
  constructor(scale: Float) : this(scale = scale, isDark = false, isStroke = false)

  internal val key: Float
    get() = scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)

  internal val name: String
    get() = "icons@$scale${if (isDark) "_d" else ""}${if (isStroke) "_s" else ""}"
}

@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private val store: MVStore
  private val classifierToMap = ConcurrentHashMap<Float, MVMap<LongArray, ImageValue>>(10)
  private val mapBuilder: MVMap.Builder<LongArray, ImageValue>

  init {
    store = openOrRecreateStore(dbFile) { LOG }
    val mapBuilder = MVMap.Builder<LongArray, ImageValue>()
    mapBuilder.keyType(ImageKeyDescriptor)
    mapBuilder.valueType(ImageValueExternalizer)
    this.mapBuilder = mapBuilder
  }

  fun close() {
    store.close()
  }

  fun save() {
    store.commit()
  }

  fun loadFromCache(themeDigest: ByteArray, imageBytes: ByteArray, mapper: SvgCacheMapper): BufferedImage? {
    val key = getCacheKey(imageBytes = imageBytes, themeDigest = themeDigest)
    val map = getMap(mapper = mapper, classifierToMap = classifierToMap, store = store, mapBuilder = mapBuilder)
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val data = map.get(key) ?: return null
      val image = readImage(data)
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
    val key = getCacheKey(imageBytes = imageBytes, themeDigest = themeDigest)
    val map = getMap(mapper = mapper, classifierToMap = classifierToMap, store = store, mapBuilder = mapBuilder)
    map.put(key, writeImage(image))
  }
}

private val ZERO_POINT = Point(0, 0)

private fun getCacheKey(imageBytes: ByteArray, themeDigest: ByteArray): LongArray {
  return longArrayOf(
    Xxh3.hash(imageBytes),
    // and another hash with seed to reduce the chance of collision
    // (https://github.com/Cyan4973/xxHash/wiki/Collision-ratio-comparison#testing-128-bit-hashes-)
    Xxh3.seededHash(imageBytes, 4812324275),
    Xxh3.hash(themeDigest)
  )
}

private fun createImage(w: Int, h: Int, dataBuffer: DataBufferInt): BufferedImage {
  val colorModel = ColorModel.getRGBdefault() as DirectColorModel
  val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, ZERO_POINT)
  @Suppress("UndesirableClassUsage")
  return BufferedImage(colorModel, raster, false, null)
}

private fun readImage(value: ImageValue): BufferedImage {
  val dataBuffer = DataBufferInt(value.data, value.data.size)
  SunWritableRaster.makeTrackable(dataBuffer)
  return createImage(value.w, value.h, dataBuffer)
}

internal fun readImage(buffer: ByteBuffer, w: Int, h: Int): Image {
  val dataBuffer = DataBufferInt(w * h)
  buffer.asIntBuffer().get(SunWritableRaster.stealData(dataBuffer, 0))
  SunWritableRaster.makeTrackable(dataBuffer)
  return createImage(w = w, h = h, dataBuffer = dataBuffer)
}

private fun writeImage(image: BufferedImage): ImageValue {
  val w = image.width
  val h = image.height

  val dataBuffer = if (image.type == BufferedImage.TYPE_INT_ARGB) {
    image.raster.dataBuffer
  }
  else {
    @Suppress("UndesirableClassUsage")
    val convertedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = convertedImage.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()
    convertedImage.raster.dataBuffer
  }
  return ImageValue(data = SunWritableRaster.stealData(dataBuffer as DataBufferInt, 0), w = w, h = h)
}

private object ImageKeyDescriptor : BasicDataType<LongArray>() {
  override fun getMemory(obj: LongArray?): Int = 3 * Long.SIZE_BYTES

  override fun compare(a: LongArray, b: LongArray): Int {
    return Arrays.compare(a, b)
  }

  override fun createStorage(size: Int): Array<LongArray?> = arrayOfNulls(size)

  override fun read(buff: ByteBuffer): LongArray {
    return longArrayOf(buff.getLong(), buff.getLong(), buff.getLong())
  }

  override fun read(buff: ByteBuffer, storage: Any?, len: Int) {
    val longBuffer = buff.asLongBuffer()
    @Suppress("UNCHECKED_CAST")
    val result = storage as Array<LongArray>
    for (i in 0 until len) {
      result[i] = longArrayOf(longBuffer.get(), longBuffer.get(), longBuffer.get())
    }
    buff.position(buff.position() + (longBuffer.position() * Long.SIZE_BYTES))
  }

  override fun write(buff: WriteBuffer, obj: LongArray) {
    buff.putLong(obj[0])
    buff.putLong(obj[1])
    buff.putLong(obj[2])
  }
}

private object ImageValueExternalizer : BasicDataType<ImageValue>() {
  override fun getMemory(obj: ImageValue): Int = (obj.data.size + 2) * Int.SIZE_BYTES

  override fun createStorage(size: Int): Array<ImageValue?> = arrayOfNulls(size)

  override fun read(buff: ByteBuffer): ImageValue {
    val actualWidth: Int
    val actualHeight: Int
    val format = buff.get().toInt() and 0xFF
    @Suppress("DuplicatedCode")
    if (format < 254) {
      actualWidth = format
      actualHeight = format
    }
    else if (format == 255) {
      actualWidth = DataUtils.readVarInt(buff)
      actualHeight = actualWidth
    }
    else {
      actualWidth = DataUtils.readVarInt(buff)
      actualHeight = DataUtils.readVarInt(buff)
    }

    val ints = IntArray(actualWidth * actualHeight)
    buff.asIntBuffer().get(ints)
    buff.position(buff.position() + (ints.size * Int.SIZE_BYTES))
    return ImageValue(data = ints, w = actualWidth, h = actualHeight)
  }

  override fun write(buff: WriteBuffer, obj: ImageValue) {
    if (obj.w == obj.h) {
      if (obj.w < 254) {
        buff.put(obj.w.toByte())
      }
      else {
        buff.put(255.toByte())
        buff.putVarInt(obj.w)
      }
    }
    else {
      buff.put(254.toByte())
      buff.putVarInt(obj.w)
      buff.putVarInt(obj.h)
    }
    buff.put(obj.data)
  }
}

private class ImageValue(@JvmField val data: IntArray, @JvmField val w: Int, @JvmField val h: Int)

private fun <K, V> getMap(mapper: SvgCacheMapper,
                          classifierToMap: MutableMap<Float, MVMap<K, V>>,
                          store: MVStore,
                          mapBuilder: MVMap.MapBuilder<MVMap<K, V>, K, V>): MVMap<K, V> {
  return classifierToMap.computeIfAbsent(mapper.key) {
    synchronized(store) {
      store.openMap(mapper.name, mapBuilder)
    }
  }
}