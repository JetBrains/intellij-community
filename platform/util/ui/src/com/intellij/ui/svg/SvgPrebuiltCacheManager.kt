// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.ImageLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.ikv.Ikv
import org.jetbrains.ikv.UniversalHash
import java.awt.Image
import java.nio.ByteBuffer
import java.nio.file.Path

private val intKeyHash = UniversalHash.IntHash()

@ApiStatus.Internal
class SvgPrebuiltCacheManager(private val dbDir: Path) {
  private val lightStores = Stores("")
  private val darkStores = Stores("-d")

  @Suppress("PropertyName")
  private inner class Stores(classifier: String) {
    val s1 = StoreContainer(1f, classifier)
    val s1_25 = StoreContainer(1.25f, classifier)
    val s1_5 = StoreContainer(1.5f, classifier)
    val s2 = StoreContainer(2f, classifier)
    val s2_5 = StoreContainer(2.5f, classifier)
  }

  private inner class StoreContainer(private val scale: Float, private val classifier: String) {
    @Volatile
    private var store: Ikv.SizeUnawareIkv<Int>? = null

    fun getOrCreate() = store ?: getSynchronized()

    @Synchronized
    private fun getSynchronized(): Ikv.SizeUnawareIkv<Int> {
      var store = store
      if (store == null) {
        store = Ikv.loadSizeUnawareIkv(dbDir.resolve("icons-v1-$scale$classifier.db"), intKeyHash)
        this.store = store
      }
      return store
    }
  }

  fun loadFromCache(key: Int, scale: Float, isDark: Boolean, docSize: ImageLoader.Dimension2DDouble): Image? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val list = if (isDark) darkStores else lightStores
    // not supported scale
    val store = when (scale) {
      1f -> list.s1.getOrCreate()
      1.25f -> list.s1_25.getOrCreate()
      1.5f -> list.s1_5.getOrCreate()
      2f -> list.s2.getOrCreate()
      2.5f -> list.s2_5.getOrCreate()
      else -> return null
    }
    val data = store.getUnboundedValue(key) ?: return null

    val actualWidth: Int
    val actualHeight: Int
    val format = data.get().toInt() and 0xff
    if (format < 254) {
      actualWidth = format
      actualHeight = format
    }
    else if (format == 255) {
      actualWidth = readVar(data)
      actualHeight = actualWidth
    }
    else {
      actualWidth = readVar(data)
      actualHeight = readVar(data)
    }

    docSize.setSize((actualWidth / scale).toDouble(), (actualHeight / scale).toDouble())

    val image = SvgCacheManager.readImage(data, actualWidth, actualHeight)
    IconLoadMeasurer.svgPreBuiltLoad.end(start)
    return image
  }
}

private fun readVar(buf: ByteBuffer): Int {
  var aByte = buf.get().toInt()
  var value: Int = aByte and 127
  if (aByte and 128 != 0) {
    aByte = buf.get().toInt()
    value = value or (aByte and 127 shl 7)
    if (aByte and 128 != 0) {
      aByte = buf.get().toInt()
      value = value or (aByte and 127 shl 14)
      if (aByte and 128 != 0) {
        aByte = buf.get().toInt()
        value = value or (aByte and 127 shl 21)
        if (aByte and 128 != 0) {
          value = value or (buf.get().toInt() shl 28)
        }
      }
    }
  }
  return value
}