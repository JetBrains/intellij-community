// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.hasher
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.seededHasher
import com.intellij.util.SVGLoader
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.sqlite.*
import sun.awt.image.SunWritableRaster
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

@JvmInline
@ApiStatus.Internal
value class SvgCacheClassifier(internal val key: Int) {
  constructor(scale: Float) : this(scale.toBits())

  constructor(scale: Float, isDark: Boolean, isStroke: Boolean) :
    this((scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)).toBits())

  constructor(scale: Float, size: Int) : this((scale + (10_000 + size)).toBits())
}

fun getSvgIconCacheFile(): Path = Path.of(PathManager.getSystemPath(), "icon-v10.db")

fun getSvgIconCacheInvalidMarkerFile(file: Path): Path = file.parent.resolve("${file.fileName}.invalidated")

@get:ApiStatus.Internal
val svgCache: SvgCacheManager? by lazy {
  try {
    if (java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true")) &&
        !System.getProperty("java.awt.headless", "false").toBoolean()) {
      SvgCacheManager(getSvgIconCacheFile())
    }
    else {
      null
    }
  }
  catch (e: Exception) {
    logger<SVGLoader>().error("Cannot open SVG cache", e)
    null
  }
}

@Suppress("SqlResolve")
@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private val connection: SqliteConnection
  private val selectStatementPool: SqlStatementPool<LongBinder>
  private val selectPrecomputedStatementPool: SqlStatementPool<LongBinder>
  private val insertStatementPool: SqlStatementPool<ObjectBinder>
  private val insertPrecomputedStatementPool: SqlStatementPool<ObjectBinder>

  init {
    val markerFile = getSvgIconCacheInvalidMarkerFile(dbFile)
    if (Files.exists(markerFile)) {
      Files.deleteIfExists(dbFile)
      Files.deleteIfExists(markerFile)
    }

    var isNew = Files.notExists(dbFile)
    connection = try {
      SqliteConnection(dbFile)
    }
    catch (e: Exception) {
      logger<SvgCacheManager>().warn(e)
      Files.deleteIfExists(dbFile)
      isNew = true
      SqliteConnection(dbFile)
    }
    if (isNew) {
      connection.execute(TABLE_SCHEMA)
    }

    selectStatementPool = SqlStatementPool(sql = "select data, w, h from image where key1 = ? and key2 = ? and kind = ? and theme = ?",
                                           connection = connection) { LongBinder(4) }
    selectPrecomputedStatementPool = SqlStatementPool(sql = "select data, w, h from precomputed_image where key = ? and theme = ?",
                                                      connection = connection) { LongBinder(2) }

    insertStatementPool = SqlStatementPool(
      sql = "insert or replace into image (key1, key2, kind, theme, w, h, data) values(?, ?, ?, ?, ?, ?, ?) ",
      connection = connection) { ObjectBinder(7) }
    insertPrecomputedStatementPool = SqlStatementPool(
      sql = "insert or replace into precomputed_image (key, theme, w, h, data) values(?, ?, ?, ?, ?) ",
      connection = connection) { ObjectBinder(5) }
  }

  internal fun isActive(): Boolean = !connection.isClosed

  fun close() {
    selectStatementPool.close()
    selectPrecomputedStatementPool.close()
    insertStatementPool.close()
    insertPrecomputedStatementPool.close()

    logger<SvgCacheManager>().info("SVG icon cache is closed")
    connection.close()
  }

  fun save() {
  }

  fun loadPrecomputedFromCache(precomputedCacheKey: Int, themeKey: Long, compoundKey: SvgCacheClassifier): BufferedImage? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val key = precomputeKeyAndKindToCacheKey(precomputedCacheKey = precomputedCacheKey, compoundKey = compoundKey)
    return selectPrecomputedStatementPool.use { statement, binder ->
      binder.bind(v1 = key, v2 = themeKey)

      val result = readImage(statement)
      IconLoadMeasurer.svgCacheRead.end(start)
      result
    }
  }

  fun loadFromCache(imageBytes: ByteArray, themeKey: Long, compoundKey: SvgCacheClassifier): BufferedImage? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    return selectStatementPool.use { statement, binder ->
      val kind = compoundKey.key.toLong()

      binder.bind(v1 = hasher.hashBytesToLong(imageBytes), v2 = seededHasher.hashBytesToLong(imageBytes), v3 = kind, v4 = themeKey)
      val result = readImage(statement)
      IconLoadMeasurer.svgCacheRead.end(start)
      result
    }
  }

  fun storeLoadedImage(precomputedCacheKey: Int,
                       themeKey: Long,
                       imageBytes: ByteArray,
                       compoundKey: SvgCacheClassifier,
                       image: BufferedImage) {
    val data = writeImage(image)
    if (precomputedCacheKey == 0) {
      val key1 = hasher.hashBytesToLong(imageBytes)
      val key2 = seededHasher.hashBytesToLong(imageBytes)
      insertStatementPool.use { statement, binder ->
        binder.bind(v1 = key1,
                    v2 = key2,
                    v3 = compoundKey.key.toLong(),
                    v4 = themeKey,
                    v5 = image.width,
                    v6 = image.height,
                    v7 = data)
        statement.executeUpdate()
      }
    }
    else {
      val key = precomputeKeyAndKindToCacheKey(precomputedCacheKey = precomputedCacheKey, compoundKey = compoundKey)
      insertPrecomputedStatementPool.use { statement, binder ->
        binder.bind(v1 = key, v2 = themeKey, v3 = image.width, v4 = image.height, v5 = data)
        statement.executeUpdate()
      }
    }
  }
}

internal fun themeDigestToCacheKey(themeDigest: LongArray): Long {
  return when (themeDigest.size) {
    0 -> 0
    1 -> themeDigest.first()
    else -> hasher.hashStream().putLongArray(themeDigest).asLong
  }
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

private fun readImage(statement: SqlitePreparedStatement<LongBinder>): BufferedImage? {
  val result = statement.executeQuery()
  if (!result.next()) {
    return null
  }

  val data = result.getBytes(0)!!
  val dataBuffer = DataBufferByte(data, data.size)
  SunWritableRaster.makeTrackable(dataBuffer)
  val w = result.getInt(1)
  val h = result.getInt(2)
  val raster = Raster.createInterleavedRaster(
    dataBuffer,
    w,
    h,
    w * 4, 4,
    bgraBandOffsets,
    ZERO_POINT
  )
  @Suppress("UndesirableClassUsage")
  return BufferedImage(colorModel, raster, false, null)
}

private fun precomputeKeyAndKindToCacheKey(precomputedCacheKey: Int, compoundKey: SvgCacheClassifier): Long {
  return (precomputedCacheKey.toLong() shl 32) or (compoundKey.key.toLong() and 0xffffffffL)
}

@Language("SQLite")
private const val TABLE_SCHEMA = """
  begin transaction;
  
  -- key2 is 0 for precomputed cache key
  create table image (
    key1 integer not null,
    key2 integer not null,
    kind integer not null,
    theme integer not null,
    w integer not null,
    h integer not null,
    data blob not null,
    primary key (key1, key2, theme, kind)
  ) strict;
  
  -- key2 is 0 for precomputed cache key
  create table precomputed_image (
    key integer not null,
    theme integer not null,
    w integer not null,
    h integer not null,
    data blob not null,
    primary key (key, theme)
  ) strict;
  
  commit transaction;
"""
