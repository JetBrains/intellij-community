// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.ui.hasher
import com.intellij.ui.seededHasher
import com.intellij.util.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.image.SunWritableRaster
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.DataInput
import java.io.DataOutput
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.seconds

internal fun getSvgIconCacheDir(): Path = Path.of(PathManager.getSystemPath(), "icon-cache")

@JvmInline
@Internal
value class SvgCacheClassifier(internal val key: Int) {
  constructor(scale: Float) : this(scale.toBits())

  constructor(scale: Float, isDark: Boolean, isStroke: Boolean) :
    this((scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)).toBits())

  constructor(scale: Float, size: Int) : this((scale + (10_000 + size)).toBits())
}

private fun getSvgIconCacheInvalidMarkerFile(dir: Path): Path = dir.resolve(".invalidated")

private class IconValue(
  @JvmField var w: Int,
  @JvmField var h: Int,
  @JvmField var data: ByteArray,
)

private fun openSvgCache(dbDir: Path): PersistentHashMap<LongArray, IconValue> {
  val markerFile = getSvgIconCacheInvalidMarkerFile(dbDir)
  if (Files.exists(markerFile)) {
    NioFiles.deleteRecursively(dbDir)
  }

  val file = dbDir.resolve("icon.db")
  try {
    return createMap(file)
  }
  catch (e: CorruptedException) {
    logger<SvgCacheManager>().warn("Icon cache is corrupted (${e.message})")
  }
  catch (e: Throwable) {
    logger<SvgCacheManager>().warn("Cannot open icon cache, will be recreated", e)
  }

  NioFiles.deleteRecursively(dbDir)
  return createMap(file)
}

private fun createMap(dbFile: Path): PersistentHashMap<LongArray, IconValue> {
  return PersistentMapBuilder.newBuilder(dbFile, object : KeyDescriptor<LongArray> {
    override fun getHashCode(value: LongArray): Int {
      return when (value.size) {
        3 -> Hashing.komihash5_0().hashLongLongLongToLong(value[0], value[1], value[2]).toInt()
        2 -> Hashing.komihash5_0().hashLongLongToLong(value[0], value[1]).toInt()
        else -> value.contentHashCode()
      }
    }

    override fun save(out: DataOutput, value: LongArray) {
      out.write(value.size)
      for (l in value) {
        out.writeLong(l)
      }
    }

    override fun isEqual(val1: LongArray, val2: LongArray) = val1.contentEquals(val2)

    override fun read(input: DataInput): LongArray {
      return LongArray(input.readByte().toInt()) { input.readLong() }
    }
  }, object : DataExternalizer<IconValue> {
    override fun save(out: DataOutput, value: IconValue) {
      out.writeByte(value.w)
      out.writeByte(value.h)
      out.write(value.data)
    }

    override fun read(input: DataInput): IconValue {
      val w = java.lang.Byte.toUnsignedInt(input.readByte())
      val h = java.lang.Byte.toUnsignedInt(input.readByte())
      val data = ByteArray(w * h * Int.SIZE_BYTES)
      input.readFully(data)
      return IconValue(w, h, data)
    }
  })
    .withVersion(0)
    .build()
}

@Suppress("SqlResolve")
@Internal
class SvgCacheManager private constructor(private val map: PersistentHashMap<LongArray, IconValue>) {
  companion object {
    @Volatile
    @Internal
    @JvmField
    var svgCache: SvgCacheManager? = null

    fun invalidateCache() {
      val svgIconCacheDir = getSvgIconCacheDir()
      if (Files.isDirectory(svgIconCacheDir)) {
        val markerFile = getSvgIconCacheInvalidMarkerFile(dir = svgIconCacheDir)
        Files.write(markerFile, ByteArray(0), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
      }
    }

    suspend fun createSvgCacheManager(cacheDir: Path = getSvgIconCacheDir()): SvgCacheManager? {
      if (!java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))) {
        return null
      }

      try {
        return withTimeout(30.seconds) {
          withContext(Dispatchers.IO) {
            SvgCacheManager(openSvgCache(cacheDir))
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
      map.force()
    }
  }

  fun markCorrupted() {
    thisLogger().info("invalidate and disable icon cache")
    invalidateCache()
    isDeactivated = true
  }

  fun close() {
    if (!map.isClosed) {
      map.close()
      thisLogger().info("SVG icon cache is closed")
    }
  }

  fun loadFromCache(key: LongArray): BufferedImage? {
    val value = map.get(key) ?: return null
    val result = readImage(value)
    return result
  }

  fun storeLoadedImage(key: LongArray, image: BufferedImage) {
    val value = IconValue(image.width, image.height, writeImage(image))
    // don't save large images
    if (value.w <= 255 && value.h <= 255) {
      map.put(key, value)
    }
  }
}

internal fun createPrecomputedIconCacheKey(precomputedCacheKey: Int, compoundKey: SvgCacheClassifier, themeKey: Long): LongArray {
  return longArrayOf(
    packTwoIntsToLong(precomputedCacheKey, compoundKey.key),
    themeKey,
  )
}

@Internal
fun createIconCacheKey(imageBytes: ByteArray, compoundKey: SvgCacheClassifier, themeKey: Long): LongArray {
  return longArrayOf(
    hasher.hashBytesToLong(imageBytes),
    packTwoIntsToLong(seededHasher.hashBytesToInt(imageBytes), compoundKey.key),
    themeKey,
  )
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

private fun packTwoIntsToLong(int1: Int, int2: Int): Long {
  return (int1.toLong() shl 32) or (int2.toLong() and 0xffffffffL)
}