// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ArrayUtilRt
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.io.mvstore.createOrResetMvStore
import com.intellij.util.io.mvstore.markMvStoreDbAsInvalid
import com.intellij.util.io.mvstore.openOrResetMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.BasicDataType
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.image.SunWritableRaster
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration.Companion.seconds

internal fun getSvgIconCacheFile(): Path = Path.of(PathManager.getSystemPath(), "icon-cache-v1.db")

@JvmInline
@Internal
value class SvgCacheClassifier(internal val key: Int) {
  constructor(scale: Float) : this(scale.toBits())

  constructor(scale: Float, isDark: Boolean, isStroke: Boolean) :
    this((scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)).toBits())

  constructor(scale: Float, size: Int) : this((scale + (10_000 + size)).toBits())
}

private class IconValue(
  @JvmField var w: Int,
  @JvmField var h: Int,
  @JvmField var data: ByteArray,
)

private fun openSvgCache(store: MVStore, name: String, logSupplier: () -> Logger): MVMap<LongArray, IconValue> {
  val mapBuilder = MVMap.Builder<LongArray, IconValue>()
  mapBuilder.setKeyType(SvgCacheKeyType)
  mapBuilder.setValueType(SvgCacheValueType)
  return openOrResetMap(store = store, name = name, mapBuilder = mapBuilder, logSupplier = logSupplier)
}

private object SvgCacheValueType : BasicDataType<IconValue>() {
  override fun getMemory(obj: IconValue) = 2 + obj.data.size

  override fun write(buff: WriteBuffer, value: IconValue) {
    buff.put(value.w.toByte())
    buff.put(value.h.toByte())
    buff.put(value.data)
  }

  override fun read(buff: ByteBuffer): IconValue {
    val w = java.lang.Byte.toUnsignedInt(buff.get())
    val h = java.lang.Byte.toUnsignedInt(buff.get())
    val bytes = ByteArray(w * h * Int.SIZE_BYTES)
    buff.get(bytes)
    return IconValue(w, h, bytes)
  }

  override fun createStorage(size: Int): Array<IconValue?> = arrayOfNulls(size)
}

private object SvgCacheKeyType : BasicDataType<LongArray>() {
  override fun getMemory(obj: LongArray) = 2 * Long.SIZE_BYTES

  override fun write(buff: WriteBuffer, obj: LongArray) {
    buff.putLong(obj[0])
    buff.putLong(obj[1])
  }

  override fun read(buff: ByteBuffer): LongArray {
    return longArrayOf(buff.getLong(), buff.getLong())
  }

  override fun compare(a: LongArray, b: LongArray) = Arrays.compare(a, b)

  override fun createStorage(size: Int): Array<LongArray?> = arrayOfNulls(size)
}

@Suppress("SqlResolve")
@Internal
class SvgCacheManager private constructor(
  private val prebuiltMap: MVMap<LongArray, IconValue>,
  private val map: MVMap<LongArray, IconValue>,
) {
  companion object {
    @Volatile
    @Internal
    @JvmField
    var svgCache: SvgCacheManager? = null

    fun invalidateCache() {
      markMvStoreDbAsInvalid(getSvgIconCacheFile())
    }

    suspend fun createSvgCacheManager(cacheFile: Path = getSvgIconCacheFile()): SvgCacheManager? {
      if (!java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))) {
        return null
      }

      try {
        return withTimeout(30.seconds) {
          withContext(Dispatchers.IO) {
            val logSupplier = ::thisLogger
            val store = createOrResetMvStore(cacheFile, readOnly = false, logSupplier)
            SvgCacheManager(
              prebuiltMap = openSvgCache(store, "prebuilt-icon-cache", logSupplier),
              map = openSvgCache(store, "icon-cache", logSupplier),
            )
          }
        }
      }
      catch (e: TimeoutCancellationException) {
        logger<SvgCacheManager>().error("Cannot create SvgCacheManager in 30 seconds", e)
        return null
      }
      catch (e: Throwable) {
        logger<SvgCacheManager>().error("Cannot create SvgCacheManager", e)
        return null
      }
    }
  }

  private var isDeactivated = false

  internal fun isActive(): Boolean = !isDeactivated && !map.isClosed

  fun save() {
    if (!map.isClosed) {
      map.store.tryCommit()
    }
  }

  fun markCorrupted() {
    thisLogger().info("invalidate and disable icon cache")
    invalidateCache()
    isDeactivated = true
  }

  fun close() {
    if (!map.isClosed) {
      map.store.close()
      thisLogger().info("SVG icon cache is closed")
    }
  }

  fun loadFromCache(key: LongArray, isPrecomputed: Boolean): BufferedImage? {
    val value = (if (isPrecomputed) prebuiltMap else map).get(key) ?: return null
    return readImage(value)
  }

  fun storeLoadedImage(key: LongArray, image: BufferedImage, isPrecomputedKey: Boolean) {
    val w = image.width
    val h = image.height
    // don't save large images
    if (w <= 255 && h <= 255) {
      (if (isPrecomputedKey) prebuiltMap else map).put(key, IconValue(w = w, h = h, data = writeImage(image)))
    }
  }
}

internal fun createPrecomputedIconCacheKey(precomputedCacheKey: Int,
                                           compoundKey: SvgCacheClassifier,
                                           colorPatcherDigest: LongArray?): LongArray {
  return longArrayOf(
    packTwoIntToLong(precomputedCacheKey, compoundKey.key),
    Hashing.komihash5_0().hashStream().putLongArray(colorPatcherDigest ?: ArrayUtilRt.EMPTY_LONG_ARRAY).asLong,
  )
}

private fun packTwoIntToLong(v1: Int, v2: Int): Long {
  return (v1.toLong() shl 32) or (v2.toLong() and 0xffffffffL)
}

@Internal
fun createIconCacheKey(imageBytes: ByteArray, compoundKey: SvgCacheClassifier, colorPatcherDigest: LongArray?): LongArray {
  val hashStream = Hashing.komihash5_0().hashStream()
  val hashStream2 = InsecureHashBuilder.seededHasher.hashStream()

  val effectiveColorPatcherDigest = colorPatcherDigest ?: ArrayUtilRt.EMPTY_LONG_ARRAY
  hashStream.putLongArray(effectiveColorPatcherDigest)
  hashStream2.putLongArray(effectiveColorPatcherDigest)

  hashStream.putBytes(imageBytes)
  hashStream.putInt(imageBytes.size)

  hashStream2.putBytes(imageBytes)
  hashStream2.putInt(imageBytes.size)

  hashStream.putInt(compoundKey.key)
  hashStream2.putInt(compoundKey.key)

  return longArrayOf(hashStream.asLong, hashStream2.asLong)
}

// BGRA order
private val bgraBandOffsets = intArrayOf(2, 1, 0, 3)

private val colorModel = ComponentColorModel(
  ColorSpace.getInstance(ColorSpace.CS_sRGB),
  true,
  false,
  Transparency.TRANSLUCENT,
  DataBuffer.TYPE_BYTE
)

private val ZERO_POINT = Point(0, 0)

private fun writeImage(image: BufferedImage): ByteArray {
  val w = image.width
  val h = image.height

  when (image.type) {
    BufferedImage.TYPE_4BYTE_ABGR -> {
      return SunWritableRaster.stealData(image.raster.dataBuffer as DataBufferByte, 0)
    }
    BufferedImage.TYPE_INT_ARGB -> {
      val data = SunWritableRaster.stealData(image.raster.dataBuffer as DataBufferInt, 0)
      val buffer = ByteBuffer.allocate(data.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      buffer.asIntBuffer().put(data)
      return buffer.array()
    }
    else -> {
      @Suppress("UndesirableClassUsage")
      val convertedImage = BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR)
      val g = convertedImage.createGraphics()
      g.drawImage(image, 0, 0, null)
      g.dispose()
      return SunWritableRaster.stealData(convertedImage.raster.dataBuffer as DataBufferByte, 0)
    }
  }
}

private fun readImage(value: IconValue): BufferedImage {
  val dataBuffer = DataBufferByte(value.data, value.data.size)
  SunWritableRaster.makeTrackable(dataBuffer)
  val w = value.w
  val h = value.h
  val raster = Raster.createInterleavedRaster(
    dataBuffer,
    w,
    h,
    w * 4, 4,
    bgraBandOffsets,
    ZERO_POINT
  )
  @Suppress("UndesirableClassUsage")
  return BufferedImage(/* cm = */ colorModel, /* raster = */ raster, /* isRasterPremultiplied = */ false, /* properties = */ null)
}