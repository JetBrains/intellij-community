// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.SVGLoader
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.sqlite.*
import org.jetbrains.xxh3.Xxh3
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
  constructor(scale: Float) : this(scale = scale, isDark = false, isStroke = false)

  constructor(scale: Float, isDark: Boolean, isStroke: Boolean) :
    this((scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)).toBits())
}

@get:ApiStatus.Internal
val svgCache: SvgCacheManager? by lazy {
  try {
    if (java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))) {
      SvgCacheManager(Path.of(PathManager.getSystemPath(), "icon-v8.db"))
    }
    else {
      null
    }
  }
  catch (e: Exception) {
    logger<SVGLoader>().error(e)
    null
  }
}

@Suppress("SqlResolve")
@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private var connection: SqliteConnection
  private val selectStatementPool: SqlStatementPool<LongBinder>
  private val selectPrecomputedStatementPool: SqlStatementPool<LongBinder>
  private val insertStatementPool: SqlStatementPool<ObjectBinder>
  private val insertPrecomputedStatementPool: SqlStatementPool<ObjectBinder>

  init {
    val isNew = Files.notExists(dbFile)
    connection = SqliteConnection(dbFile)
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

  fun close() {
    selectStatementPool.close()
    selectPrecomputedStatementPool.close()
    insertStatementPool.close()
    insertPrecomputedStatementPool.close()
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
      binder.bind(v1 = Xxh3.hash(imageBytes), v2 = Xxh3.seededHash(imageBytes, SEED), v3 = kind, v4 = themeKey)

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
      val key1 = Xxh3.hash(imageBytes)
      val key2 = Xxh3.seededHash(imageBytes, SEED)
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
    else -> Xxh3.hashLongs(themeDigest)
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

private const val SEED = 4812324275L

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
