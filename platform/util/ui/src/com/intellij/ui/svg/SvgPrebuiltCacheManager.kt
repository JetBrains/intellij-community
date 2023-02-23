// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.icons.IconLoadMeasurer
import org.h2.mvstore.DataUtils
import org.jetbrains.ikv.Ikv
import java.awt.Image
import java.nio.file.Path

internal class SvgPrebuiltCacheManager(dbDir: Path) {
  private val lightStores = Stores(dbDir, "")
  private val darkStores = Stores(dbDir, "-d")

  fun loadFromCache(key: Int, mapper: SvgCacheMapper): Image? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val list = if (mapper.isDark) darkStores else lightStores
    val store = if (mapper.scale == 1f) list.s1.getOrCreate()
    else if (mapper.scale == 2f) list.s2.getOrCreate()
    else if (mapper.scale == 1.25f && SystemInfoRt.isWindows) list.s1_25.getOrCreate()
    else if (mapper.scale == 1.5f && SystemInfoRt.isWindows) list.s1_5.getOrCreate()
    else return null  // unsupported scale

    val data = store.getUnboundedValue(key) ?: return null
    val actualWidth: Int
    val actualHeight: Int
    val format = data.get().toInt() and 0xff
    @Suppress("DuplicatedCode")
    if (format < 254) {
      actualWidth = format
      actualHeight = format
    }
    else if (format == 255) {
      actualWidth = DataUtils.readVarInt(data)
      actualHeight = actualWidth
    }
    else {
      actualWidth = DataUtils.readVarInt(data)
      actualHeight = DataUtils.readVarInt(data)
    }

    val image = readImage(buffer = data, w = actualWidth, h = actualHeight)
    IconLoadMeasurer.svgPreBuiltLoad.end(start)
    return image
  }
}

@Suppress("PropertyName")
private class Stores(dbDir: Path, classifier: String) {
  val s1 = StoreContainer(dbDir, 1f, classifier)
  val s1_25 = StoreContainer(dbDir, 1.25f, classifier)
  val s1_5 = StoreContainer(dbDir, 1.5f, classifier)
  val s2 = StoreContainer(dbDir, 2f, classifier)
}

private class StoreContainer(private val dbDir: Path, private val scale: Float, private val classifier: String) {
  @Volatile
  private var store: Ikv.SizeUnawareIkv? = null

  fun getOrCreate() = store ?: getSynchronized()

  @Synchronized
  private fun getSynchronized(): Ikv.SizeUnawareIkv {
    var store = store
    if (store == null) {
      store = Ikv.loadSizeUnawareIkv(dbDir.resolve("icon-v4-$scale$classifier.db"))
      this.store = store
    }
    return store
  }
}

