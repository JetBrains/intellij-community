// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.util.ImageLoader
import com.intellij.util.SVGLoaderCacheBasics
import com.intellij.util.ui.paint.ImageComparator
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL

class SVGLoaderCacheTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `no entry`() = runTest {
    Assert.assertNull(
      loadFromCache("", "file://mock".toByteArray(), 1.0, null)
    )
  }

  private fun fixImage(i: Image) : BufferedImage {
    val width = i.getWidth(null)
    val height = i.getHeight(null)
    @Suppress("UndesirableClassUsage")
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = bufferedImage.createGraphics()
    g.drawImage(i, 0, 0, null)
    g.dispose()
    return bufferedImage
  }

  @Test
  fun `save and load`() = runTest {

    val i = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
    i.setRGB(0, 0, 0xff00ff)
    i.setRGB(0, 1, 0x00ff00)

    val imageBytes = "file://mock".toByteArray()
    storeLoadedImage("", imageBytes, 1.0, i, ImageLoader.Dimension2DDouble(20.0, 15.0))

    val copySize = ImageLoader.Dimension2DDouble(0.0, 0.0)
    val copy = loadFromCache("", imageBytes, 1.0, copySize)

    Assert.assertEquals(20.0, copySize.width, 0.1)
    Assert.assertEquals(15.0, copySize.height, 0.1)

    Assert.assertNotNull(copy)
    copy!!

    ImageComparator.compareAndAssert(ImageComparator.AASmootherComparator(0.1, 0.1, Color(0, 0, 0, 0)), i, fixImage(copy), null)

    Assert.assertNull(
      loadFromCache("A", "file://mock".toByteArray(), 1.0, null)
    )
    Assert.assertNull(
      loadFromCache("", "file://mock-2".toByteArray(), 1.0, null)
    )
    Assert.assertNull(
      loadFromCache("", "file://mock".toByteArray(), 2.0, null)
    )
  }


  private fun runTest(action: SVGLoaderCacheBasics.() -> Unit) {
    val newFolder = temp.newFolder()

    val it = object :SVGLoaderCacheBasics() {
      override val cachesHome: File
        get() {
          return newFolder
        }

      override fun forkIOTask(action: () -> Unit) {
        action()
      }
    }

    it.action()
  }

}