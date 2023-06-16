// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.RestoreScaleRule
import com.intellij.ui.scale.JBUIScale.setSystemScaleFactor
import com.intellij.ui.scale.JBUIScale.setUserScaleFactor
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.TestScaleHelper.loadImage
import com.intellij.ui.scale.TestScaleHelper.overrideJreHiDPIEnabled
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.ui.svg.renderSvgWithSize
import com.intellij.util.SVGLoader
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests that [SVGLoader] correctly interprets SVG document size.
 */
class SvgIconSizeTest {
  @Test
  fun test() {
    setUserScaleFactor(1f)
    overrideJreHiDPIEnabled(true)
    test(ScaleContext.create(ScaleType.SYS_SCALE.of(1.0)))
    test(ScaleContext.create(ScaleType.SYS_SCALE.of(2.0)))
    val currentSysScale = sysScale()
    if (currentSysScale != 2f) {
      setSystemScaleFactor(2f)
      /*
       * Test with the system scale equal to the current system scale.
       */test(ScaleContext.create(ScaleType.SYS_SCALE.of(2.0)))
      setSystemScaleFactor(currentSysScale)
    }

    /*
     * Test overridden size.
     */
    val file = getSvgIconPath("20x10")
    val scaleContext = ScaleContext.create(ScaleType.SYS_SCALE.of(2.0))
    val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
    val data = Files.readAllBytes(file)
    val image = renderSvgWithSize(inputStream = data.inputStream(), width = 25f, height = 15f, scale = pixScale.toFloat())
    assertThat(image).isNotNull()
    assertThat(image.width.toDouble()).isEqualTo(pixScale * 25)
    assertThat(image.height.toDouble()).isEqualTo(pixScale * 15)

    /*
     * Test SVGLoader.getDocumentSize for SVG starting with <svg.
     */
    var size = getSvgDocumentSize(data)
    TestCase.assertEquals("wrong svg doc width", 20.0, size.getWidth())
    TestCase.assertEquals("wrong svg doc height", 10.0, size.getHeight())

    /*
     * Test SVGLoader.getDocumentSize for SVG starting with <?xml.
     */
    val file2 = getSvgIconPath("xml_20x10")
    size = getSvgDocumentSize(Files.readAllBytes(file2))
    TestCase.assertEquals("wrong svg doc width", 20.0, size.getWidth())
    TestCase.assertEquals("wrong svg doc height", 10.0, size.getHeight())
  }

  companion object {
    @ClassRule
    @JvmField
    val manageState: ExternalResource = RestoreScaleRule()

    private fun test(ctx: ScaleContext) {
      val scale = ctx.getScale(ScaleType.SYS_SCALE).toInt()

      /*
     * Test default unit ("px").
     */
      var image = loadImage(getSvgIconPath("20x10"), ctx)
      TestCase.assertNotNull(image)
      TestCase.assertEquals("wrong image width", 20 * scale, image.width)
      TestCase.assertEquals("wrong image height", 10 * scale, image.height)

      /*
     * Test "px" unit.
     */image = loadImage(getSvgIconPath("20px10px"), ctx)
      TestCase.assertNotNull(image)
      TestCase.assertEquals("wrong image width", 20 * scale, image.width)
      TestCase.assertEquals("wrong image height", 10 * scale, image.height)

      /*
     * Test default size.
     */image = loadImage(getSvgIconPath("default"), ctx)
      TestCase.assertNotNull(image)
      TestCase.assertEquals("wrong image width", SVGLoader.ICON_DEFAULT_SIZE * scale, image.width)
      TestCase.assertEquals("wrong image height", SVGLoader.ICON_DEFAULT_SIZE * scale, image.height)
    }

    private fun getSvgIconPath(size: String): Path {
      return Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/myIcon_" + size + ".svg")
    }
  }
}