// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.AdaptiveImageRenderer
import com.intellij.ui.svg.AdaptiveImageRendererEvent
import com.intellij.ui.svg.AdaptiveImageView
import com.intellij.ui.svg.AdaptiveImagesManager
import com.intellij.ui.svg.ImageDimension
import com.intellij.ui.svg.ImageDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Dimension
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage
import java.net.URI
import java.net.URL
import java.util.Hashtable
import javax.swing.text.GlyphView
import javax.swing.text.View
import kotlin.math.roundToInt

@TestApplication
internal class HtmlImageAlignmentTest {
  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `image bottom aligns with the text baseline by default`(renderer: Renderer): Unit = timeoutRunBlocking {
    for (scale in listOf(1f, 1.5f, 2f)) {
      val layout = renderImage(renderer, scale = scale)

      assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.y + layout.image.height)
    }
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `legacy middle aligns the image centre with the text baseline`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "align='middle'")

    assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.centerY.toInt())
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `legacy top alignment places the image top on the row baseline`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "align='top'")

    assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.y)
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `images do not inherit alignment from a table cell`(renderer: Renderer): Unit = timeoutRunBlocking {
    for (attributes in listOf("style='vertical-align: top'", "style='vertical-align: middle'", "align='top'", "align='middle'")) {
      val layout = renderImage(renderer, body = { "<table><tr><td $attributes>before $it after</td></tr></table>" })

      assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.y + layout.image.height)
    }
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `image stylesheet alignment takes precedence over cell alignment`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "class='badge'", body = {
      "<table><tr><td style='vertical-align: top'>before $it after</td></tr></table>"
    })

    assertThat(layout.image.centerY).isCloseTo(layout.textBaselines.first() - layout.xHeight / 2, within(1.0))
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS top and text-top position short and tall images`(renderer: Renderer): Unit = timeoutRunBlocking {
    for (alignment in listOf("top", "text-top")) {
      for (scale in listOf(1f, 1.5f, 2f)) {
        for (height in listOf(8, 24, 80)) {
          val layout = renderImage(renderer, "style='vertical-align: $alignment'", scale, imageSize = Dimension(40, height))

          if (alignment == "top") assertThat(layout.image.y).isEqualTo(layout.row.y)
          else assertThat(layout.textTops).hasSize(2).containsOnly(layout.image.y)
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS top follows the tallest text on the line`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "style='vertical-align: top'", imageSize = Dimension(40, 8), body = {
      "<p>before $it <span style='font-size: 32px'>after</span></p>"
    })

    assertThat(layout.image.y).isEqualTo(layout.textTops.min())
    assertThat(layout.textTops.first()).isGreaterThan(layout.image.y)
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS text-top follows the parent font`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "style='vertical-align: text-top'", imageSize = Dimension(40, 8), body = {
      "<p>before $it <span style='font-size: 32px'>after</span></p>"
    })

    assertThat(layout.image.y).isEqualTo(layout.textTops.first())
    assertThat(layout.image.y).isGreaterThan(layout.textTops.last())
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS top images share the line top`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "style='vertical-align: top'", imageSize = Dimension(40, 8), body = {
      "<p>before $it <img src='https://example.invalid/image.png' width='40' height='80' style='vertical-align: top'> after</p>"
    })

    assertThat(layout.otherImages).hasSize(1)
    assertThat(layout.image.y).isEqualTo(layout.otherImages.single().y)
    assertThat(layout.textTops).containsOnly(layout.image.y)
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS top follows a taller baseline image`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "style='vertical-align: top'", imageSize = Dimension(40, 8), body = {
      "<p>before $it <img src='https://example.invalid/image.png' width='40' height='80'> after</p>"
    })

    assertThat(layout.otherImages).hasSize(1)
    assertThat(layout.image.y).isEqualTo(layout.otherImages.single().y)
    assertThat(layout.textTops.first()).isGreaterThan(layout.image.y)
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS top and text-top account for line height spacing`(renderer: Renderer): Unit = timeoutRunBlocking {
    for (alignment in listOf("top", "text-top")) {
      val layout = renderImage(renderer, "style='vertical-align: $alignment'", imageSize = Dimension(40, 8), body = {
        "<p style='line-height: 64px'>before $it after</p>"
      })

      if (alignment == "top") {
        assertThat(layout.image.y).isEqualTo(layout.row.y)
        assertThat(layout.textTops.first()).isGreaterThan(layout.image.y)
      }
      else {
        assertThat(layout.textTops).hasSize(2).containsOnly(layout.image.y)
        assertThat(layout.image.y).isGreaterThan(layout.row.y)
      }
      assertThat(layout.row.height).isCloseTo(64, within(1))
    }
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS middle uses the text x-height`(renderer: Renderer): Unit = timeoutRunBlocking {
    for (scale in listOf(1f, 1.5f, 2f)) {
      for (attributes in listOf("style='vertical-align: middle'", "class='badge' align='top'", "align='absmiddle'", "align='abscenter'")) {
        val layout = renderImage(renderer, attributes, scale)

        assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.textBaselines.first())
        assertThat(layout.image.centerY)
          .describedAs("%s at scale %s", attributes, scale)
          .isCloseTo(layout.textBaselines.first() - layout.xHeight / 2, within(1.0))
      }
    }
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `inline CSS overrides the stylesheet and HTML alignment`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "class='badge' align='middle' style='vertical-align: baseline'")

    assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.y + layout.image.height)
  }

  @ParameterizedTest
  @EnumSource(Renderer::class)
  fun `CSS middle uses the resized image height`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "class='badge' width='80' height='20'", imageSize = Dimension(800, 200))

    assertThat(layout.image.size).isEqualTo(Dimension(80, 20))
    assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.textBaselines.first())
    assertThat(layout.image.centerY).isCloseTo(layout.textBaselines.first() - layout.xHeight / 2, within(1.0))
  }

  @ParameterizedTest
  @EnumSource(Renderer::class, names = ["FIT_TO_WIDTH", "FIT_TO_WIDTH_ADAPTIVE"])
  fun `CSS middle keeps automatic image resizing`(renderer: Renderer): Unit = timeoutRunBlocking {
    val layout = renderImage(renderer, "class='badge'", imageSize = Dimension(800, 200), paneWidth = 160)

    assertThat(layout.image.width).isBetween(1, 160)
    assertThat(layout.image.height).isBetween(1, 199)
  }

  private suspend fun renderImage(
    renderer: Renderer,
    attributes: String = "",
    scale: Float = 1f,
    imageSize: Dimension = Dimension(40, 24),
    paneWidth: Int = 400,
    body: (String) -> String = { "<p>before $it after</p>" },
  ): Layout = withContext(Dispatchers.UI) {
    val originalScale = JBUIScale.sysScale()
    val jreHiDpi = JreHiDpiUtil.test_jreHiDPI()
    val originalHiDpi = jreHiDpi.getAndSet(true)
    JBUIScale.setSystemScaleFactor(scale)
    try {
      val image =
        BufferedImage((imageSize.width * scale).roundToInt(), (imageSize.height * scale).roundToInt(), BufferedImage.TYPE_INT_ARGB)
      val imageUrl = URI.create("https://example.invalid/image.png").toURL()
      val configuration = JBHtmlPaneConfiguration {
        renderer.extension?.let { extensions(it) }
        customStyleSheet("img.badge { vertical-align: middle; }")
        imageResolverFactory { Hashtable<URL, Image>().apply { put(imageUrl, image) } }
      }
      val pane = JBHtmlPane(JBHtmlPaneStyleConfiguration.builder().build(), configuration)
      try {
        pane.document.putProperty(AdaptiveImageView.ADAPTIVE_IMAGES_MANAGER_PROPERTY, imageManager(image, imageSize))
        pane.text = "<html><body>${body("<img src='$imageUrl' $attributes>")}</body></html>"
        pane.size = Dimension(paneWidth, 400)
        val graphics = BufferedImage(pane.width, pane.height, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
          repeat(2) {
            pane.ui.getRootView(pane).setSize(pane.width.toFloat(), pane.height.toFloat())
            pane.paint(graphics)
          }
        }
        finally {
          graphics.dispose()
        }

        var imageBounds: Rectangle? = null
        var rowBounds: Rectangle? = null
        val otherImages = ArrayList<Rectangle>()
        val baselines = ArrayList<Int>()
        val textTops = ArrayList<Int>()
        var xHeight = 0.0
        fun visit(view: View, allocation: Shape, parentAllocation: Shape? = null) {
          if (view.element.name == "img") {
            if (imageBounds == null) {
              imageBounds = allocation.bounds
              rowBounds = parentAllocation?.bounds
            }
            else otherImages.add(allocation.bounds)
          }
          if (view is GlyphView) {
            val text = view.document.getText(view.startOffset, minOf(view.endOffset, view.document.length) - view.startOffset)
            if (text.contains("before") || text.contains("after")) {
              val metrics = pane.getFontMetrics(view.font)
              baselines.add(allocation.bounds.y + metrics.ascent)
              textTops.add(allocation.bounds.y)
              xHeight = view.font.createGlyphVector(metrics.fontRenderContext, "x").visualBounds.height
            }
          }
          for (index in 0 until view.viewCount) {
            view.getChildAllocation(index, allocation)?.let { visit(view.getView(index), it, allocation) }
          }
        }
        visit(pane.ui.getRootView(pane), Rectangle(pane.width, pane.height))
        Layout(checkNotNull(imageBounds), baselines, xHeight, textTops, otherImages, checkNotNull(rowBounds))
      }
      finally {
        Disposer.dispose(pane)
      }
    }
    finally {
      JBUIScale.setSystemScaleFactor(originalScale)
      jreHiDpi.set(originalHiDpi)
    }
  }

  private fun imageManager(image: BufferedImage, imageSize: Dimension): AdaptiveImagesManager {
    val manager = mock<AdaptiveImagesManager>()
    whenever(manager.createRenderer(any<(AdaptiveImageRendererEvent) -> Unit>())).thenAnswer {
      val listener = it.getArgument<(AdaptiveImageRendererEvent) -> Unit>(0)
      val renderer = mock<AdaptiveImageRenderer>()
      whenever(renderer.getRenderedImage()).thenReturn(image)
      doAnswer {
        val width = imageSize.width.toFloat()
        val height = imageSize.height.toFloat()
        val dimensions = ImageDimensions(width, ImageDimension.Unit.PX, height, ImageDimension.Unit.PX, width, height)
        listener(AdaptiveImageRendererEvent.Loaded(dimensions, false))
        null
      }.whenever(renderer).setOrigin(any())
      renderer
    }
    return manager
  }

  enum class Renderer(val extension: ExtendableHTMLViewFactory.Extension?) {
    DEFAULT(null),
    FIT_TO_WIDTH(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES),
    ADAPTIVE(ExtendableHTMLViewFactory.Extensions.ADAPTIVE_IMAGE_EXTENSION),
    FIT_TO_WIDTH_ADAPTIVE(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_ADAPTIVE_IMAGE_EXTENSION),
  }

  private data class Layout(
    val image: Rectangle,
    val textBaselines: List<Int>,
    val xHeight: Double,
    val textTops: List<Int>,
    val otherImages: List<Rectangle>,
    val row: Rectangle,
  )
}
