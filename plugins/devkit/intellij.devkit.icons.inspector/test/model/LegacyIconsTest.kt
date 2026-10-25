// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BadgeIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.SizedIcon
import com.intellij.ui.icons.IconWithOverlay
import com.intellij.ui.icons.IconWrapperWithToolTip
import com.intellij.ui.icons.TextIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.IconUtil
import com.intellij.util.ui.ColorIcon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JLabel

/** Dedicated expansion of legacy Swing icon classes (children in paint order, placement, appearance parameters). */
@TestApplication
class LegacyIconsTest {
  private val a = PlainIcon(16, 16, "A")
  private val b = PlainIcon(8, 8, "B")

  @BeforeEach
  fun setUp() {
    IconLoader.activate()
  }

  @AfterEach
  fun tearDown() {
    IconLoader.deactivate()
  }

  @Test
  fun `row icon children with x offsets and centered y`() {
    val icon = RowIcon(2, com.intellij.ui.icons.RowIcon.Alignment.CENTER)
    icon.setIcon(a, 0)
    icon.setIcon(b, 1)
    val root = build(icon)
    assertEquals(InspectorNodeOrigin.LEGACY, root.origin)
    assertEquals("CENTER", root.property("alignment"))
    val (first, second) = root.children
    assertEquals(a.name, first.subtitleName())
    assertEquals("0", first.property("x"))
    assertEquals("0", first.property("y"))
    assertEquals("16", second.property("x"))
    assertEquals("4", second.property("y"))
    assertEquals("CENTER", second.property("alignment"))
    assertTrue(root.children.all { it.origin == InspectorNodeOrigin.LEGACY })
  }

  @Test
  fun `layered icon with a shifted, disabled layer`() {
    val icon = LayeredIcon(2)
    icon.setIcon(a, 0)
    icon.setIcon(b, 1, 4, 2)
    icon.setLayerEnabled(1, false)
    val (base, shifted) = build(icon).children
    assertEquals("(0, 0)", base.property("shift"))
    assertEquals("(4, 2)", shifted.property("shift"))
    assertEquals("false", shifted.property("layer enabled"))
    assertTrue(shifted.subtitle!!.contains("+(4,2) disabled"), shifted.subtitle)
  }

  @Test
  fun `icon with overlay names main and overlay`() {
    val icon = object : IconWithOverlay(a, b) {
      override fun getOverlayShape(x: Int, y: Int): Shape = Rectangle(x + 8, y + 8, 8, 8)
    }
    val root = build(icon)
    val (main, overlay) = root.children
    assertTrue(main.subtitle!!.startsWith("main"), main.subtitle)
    assertTrue(overlay.subtitle!!.startsWith("overlay"), overlay.subtitle)
    assertEquals("(8, 8) 8×8", overlay.property("overlay cut-out shape"))
  }

  @Test
  fun `badge icon shows its paint and badge bounds`() {
    val root = build(BadgeIcon(a, Color.RED))
    assertTrue(root.property("badge paint")!!.startsWith("#FF0000FF"), root.property("badge paint"))
    assertTrue(root.property("badge shape provider")!!.contains("BadgeDotProvider"))
    assertTrue(root.property("badge bounds") != "none")
    assertEquals("base", root.children.single().subtitle!!.substringBefore(" ·"))
  }

  @Test
  fun `sized icon centers its delegate`() {
    val root = build(SizedIcon(b, 16, 16))
    assertEquals("16×16", root.property("sized to"))
    assertEquals("(4, 4), centered", root.children.single().property("offset"))
  }

  @Test
  fun `IconUtil wrappers - scaled, resized, cropped`() {
    val scaled = build(IconUtil.scale(a, 2.0))
    assertEquals("scaled source", scaled.children.single().subtitle!!.substringBefore(" ·"))
    assertEquals("2.0", scaled.children.single().property("scale (private)"))

    val resized = build(IconUtil.resizeSquared(a, 24))
    assertEquals("resized source", resized.children.single().subtitle!!.substringBefore(" ·"))
    assertEquals("24", resized.children.single().property("size (private)"))

    val cropped = build(IconUtil.cropIcon(a, Rectangle(2, 3, 10, 10)))
    assertEquals("(2, 3) 10×10", cropped.property("crop"))
    assertEquals("(-2, -3)", cropped.children.single().property("offset"))
  }

  @Test
  fun `disabled and colorized icons show their source and filter`() = runInEdtAndWait {
    val disabled = build(IconLoader.getDisabledIcon(a))
    assertTrue(disabled.children.any { it.subtitleName() == "A" } || disabled.property("filter") != null, disabled.toString())
    assertTrue(disabled.property("filter") != null, disabled.properties.toString())

    val colorized = build(IconUtil.colorize(a, Color.BLUE))
    assertTrue(colorized.property("filter")!!.startsWith("colorize #0000FFFF"), colorized.property("filter"))
    assertEquals("A", colorized.children.single().subtitleName())
  }

  @Test
  fun `color, text, tooltip and animated icons`() {
    val color = build(ColorIcon(12, Color.GREEN, true))
    assertTrue(color.property("color")!!.startsWith("#00FF00FF"))
    assertTrue(color.property("border color") != "unavailable (private)", color.property("border color"))

    val text = build(TextIcon("Hi", JLabel(), 11f))
    assertEquals("Hi", text.property("text"))
    assertEquals("11.0", text.property("font size"))

    val tooltip = build(IconWrapperWithToolTip(a, Supplier { "tip" }))
    assertEquals("tip", tooltip.property("tooltip"))
    assertEquals("wrapped", tooltip.children.single().subtitle!!.substringBefore(" ·"))

    val animated = build(AnimatedIcon(120, a, b))
    assertEquals("2", animated.property("frames"))
    assertEquals(listOf("120 ms", "120 ms"), animated.children.map { it.property("delay") })
    assertEquals(listOf("A", "B"), animated.children.map { it.subtitleName() })
  }

  private fun build(icon: Icon): InspectorNode = InspectorTreeBuilder.buildForLegacy("l:", icon, emptyList(), null).root

  private fun InspectorNode.property(name: String): String? = properties.firstOrNull { it.name == name }?.value

  /** The PlainIcon name, from its toString shown as a row. */
  private fun InspectorNode.subtitleName(): String? = property("toString")?.removePrefix("PlainIcon:")

  private class PlainIcon(private val width: Int, private val height: Int, val name: String) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      g.fillRect(x, y, width, height)
    }

    override fun getIconWidth(): Int = width
    override fun getIconHeight(): Int = height
    override fun toString(): String = "PlainIcon:$name"
  }
}
