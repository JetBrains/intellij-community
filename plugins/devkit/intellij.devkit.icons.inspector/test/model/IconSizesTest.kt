// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.scale.factor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.roundToInt

@TestApplication
class IconSizesTest {
  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
  }

  @Test
  fun `captured SwingIcon at density 2 with a 1_25 scale`() {
    testIcons {
      val icon = SwingIcon(iconDescriptor { image(testImage(16, 16)) }, factor(1.25f))
      val component = JLabel()
      val capture = capture(icon, component, density = 2.0)

      val tree = InspectorTreeBuilder.buildFromCapture("z:", icon, capture, SizingContext(screenScale = 2f, rootPainted = null))

      val root = tree.root.sizes!!
      assertSize(16, SizeProvenance.COMPUTED, root.source)
      assertSize(40, SizeProvenance.MEASURED, root.devicePixels)
      assertSize(40, SizeProvenance.MEASURED, root.painted)

      val iconNode = tree.root.children.single().sizes!!
      assertSize(16, SizeProvenance.COMPUTED, iconNode.source)
      assertSize(40, SizeProvenance.MEASURED, iconNode.devicePixels)
      assertSize(40, SizeProvenance.MEASURED, iconNode.painted)

      val imageLayer = tree.root.children.single().children.single { it.title == "ImageIconLayer" }
      val layerSizes = imageLayer.sizes!!
      assertSize(16, SizeProvenance.COMPUTED, layerSizes.source)
      assertEquals("native image size", layerSizes.source!!.note)
      assertSize(40, SizeProvenance.MEASURED, layerSizes.devicePixels)

      // modifiers and resources have no sizes
      val resource = imageLayer.children.single { it.kind == InspectorNodeKind.RESOURCE }
      assertNull(resource.sizes)
    }
  }

  @Test
  fun `legacy scalable icon with an object scale`() {
    val icon = FakeScalableIcon(logical = 32, scale = 2f)
    val tree = InspectorTreeBuilder.buildForLegacy("z:", icon, emptyList(), null, SizingContext(screenScale = 2f, rootPainted = null))
    val sizes = tree.root.sizes!!
    assertSize(16, SizeProvenance.COMPUTED, sizes.source)
    assertSize(64, SizeProvenance.COMPUTED, sizes.devicePixels)
    assertEquals(SizeProvenance.UNAVAILABLE, sizes.painted!!.provenance)
  }

  @Test
  fun `a paint that resizes the icon is measured`() {
    val icon = FakeScalableIcon(logical = 16, scale = 1f)
    val component = runInEdtAndGet {
      ResizingComponent(icon, 1.25f).apply { setSize(40, 40) }
    }
    val picked = PickedIcon(component, component, icon, "ResizingComponent", emptyList(), null, Point(5, 5))

    val draws = runInEdtAndGet { PaintRecorder.record(component, 2f) }
    val chosen = PaintRecorder.choose(draws, Point(5, 5), 2f)
    assertNotNull(chosen)
    assertSize(40, SizeProvenance.MEASURED, chosen)

    val measured = runInEdtAndGet { PaintRecorder.measure(picked) }!!
    val screenScale = IconSizing.screenScale(component)!!
    assertEquals(SizeProvenance.MEASURED, measured.provenance)
    assertEquals((20 * screenScale).roundToInt(), measured.width)

    val tree = InspectorTreeBuilder.buildForLegacy("z:", icon, emptyList(), null, SizingContext(screenScale, measured))
    val sizes = tree.root.sizes!!
    assertEquals(SizeProvenance.COMPUTED, sizes.devicePixels!!.provenance)
    assertEquals((16 * screenScale).roundToInt(), sizes.devicePixels!!.width)
    assertEquals(SizeProvenance.MEASURED, sizes.painted!!.provenance)
    assertNotEquals(sizes.devicePixels!!.width, sizes.painted!!.width)
  }

  private fun assertSize(expected: Int, provenance: SizeProvenance, value: SizeValue?) {
    assertNotNull(value)
    assertEquals(provenance, value!!.provenance, value.toString())
    assertEquals(expected, value.width, value.toString())
    assertEquals(expected, value.height, value.toString())
  }

  private fun capture(icon: SwingIcon, component: JLabel, density: Double): RenderCapture {
    val captures = mutableListOf<RenderCapture>()
    IconRenderDebugger.capture({ it === component }) { captures.add(it) }.use {
      val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        graphics.scale(density, density)
        icon.paintIcon(component, graphics, 0, 0)
      }
      finally {
        graphics.dispose()
      }
    }
    return captures.single()
  }

  /** A legacy icon with an object scale, drawing one image of its (logical) size. */
  private class FakeScalableIcon(private val logical: Int, private val scale: Float) : ScalableIcon {
    override fun getIconWidth(): Int = logical
    override fun getIconHeight(): Int = logical
    override fun getScale(): Float = scale
    override fun scale(scaleFactor: Float): Icon = FakeScalableIcon((logical / scale * scaleFactor).roundToInt(), scaleFactor)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      @Suppress("UndesirableClassUsage")
      val image = BufferedImage(logical, logical, BufferedImage.TYPE_INT_ARGB)
      g.drawImage(image, x, y, logical, logical, null)
    }
  }

  /** Like a toolbar look: paints a background, then its icon enlarged by [factor]. */
  private class ResizingComponent(private val icon: ScalableIcon, private val factor: Float) : JComponent() {
    override fun paintComponent(g: Graphics) {
      g.fillRect(0, 0, width, height)
      icon.scale(factor).paintIcon(this, g, 2, 2)
    }
  }
}
