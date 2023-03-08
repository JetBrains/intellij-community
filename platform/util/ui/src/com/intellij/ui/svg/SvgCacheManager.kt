// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ui.icons.IconLoadMeasurer
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.sqlite.LongBinder
import org.jetbrains.sqlite.ObjectBinder
import org.jetbrains.sqlite.SqlStatementPool
import org.jetbrains.sqlite.SqliteConnection
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
value class SvgCacheMapper(val key: Int) {
  constructor(scale: Float) : this(scale = scale, isDark = false, isStroke = false)

  constructor(scale: Float, isDark: Boolean, isStroke: Boolean) :
    this((scale + (if (isDark) 1_000 else 0) + (if (isStroke) 1_100 else 0)).toBits())
}

@Suppress("SqlResolve")
@ApiStatus.Internal
class SvgCacheManager(dbFile: Path) {
  private var connection: SqliteConnection
  private val selectStatementPool: SqlStatementPool<LongBinder>
  private val insertStatementPool: SqlStatementPool<ObjectBinder>

  init {
    val isNew = Files.notExists(dbFile)
    connection = SqliteConnection(dbFile)
    if (isNew) {
      connection.execute(TABLE_SCHEMA)
    }

    selectStatementPool = SqlStatementPool(sql = "select data, w, h from image where key1 = ? and key2 = ? and kind = ? and theme = ?",
                                           connection = connection) { LongBinder(4) }

    insertStatementPool = SqlStatementPool(
      sql = "insert or replace into image (key1, key2, kind, theme, w, h, data) values(?, ?, ?, ?, ?, ?, ?) ",
      connection = connection) { ObjectBinder(7) }

  }

  fun close() {
    selectStatementPool.close()
    insertStatementPool.close()
    connection.close()
  }

  fun save() {
  }

  fun loadFromCache(precomputedCacheKey: Int, imageBytes: ByteArray?, themeDigest: LongArray, compoundKey: SvgCacheMapper): BufferedImage? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    return selectStatementPool.use { statement, binder ->
      val kind = compoundKey.key.toLong()
      val theme = themeDigestToDbValue(themeDigest)
      if (imageBytes == null) {
        binder.bind(v1 = precomputedCacheKey.toLong(), v2 = 0, v3 = kind, v4 = theme)
      }
      else {
        binder.bind(v1 = Xxh3.hash(imageBytes), v2 = Xxh3.seededHash(imageBytes, SEED), v3 = kind, v4 = theme)
      }

      val result = statement.executeQuery()
      if (!result.next()) {
        return@use null
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

      val image = BufferedImage(colorModel, raster, false, null)
      IconLoadMeasurer.svgCacheRead.end(start)
      image
    }
  }

  private fun themeDigestToDbValue(themeDigest: LongArray): Long {
    return when (themeDigest.size) {
      0 -> 0
      1 -> themeDigest.first()
      else -> Xxh3.hashLongs(themeDigest)
    }
  }

  fun storeLoadedImage(precomputedCacheKey: Int,
                       themeDigest: LongArray,
                       imageBytes: ByteArray,
                       mapper: SvgCacheMapper,
                       image: BufferedImage) {
    insertStatementPool.use { statement, binder ->
      val kind = mapper.key.toLong()
      val theme = themeDigestToDbValue(themeDigest)
      val key1 = if (precomputedCacheKey == 0) Xxh3.hash(imageBytes) else precomputedCacheKey
      val key2 = if (precomputedCacheKey == 0) Xxh3.seededHash(imageBytes, SEED) else 0
      val data = writeImage(image)
      binder.bind(key1, key2, kind, theme, image.width, image.height, data)
      statement.executeUpdate()
    }
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
  
  commit transaction;
"""
