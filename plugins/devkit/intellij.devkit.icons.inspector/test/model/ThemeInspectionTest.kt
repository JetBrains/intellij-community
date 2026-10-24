// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.dark
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.SVGLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.RGBImageFilter
import javax.swing.Icon

/**
 * The "Theme" rows of the inspector: per new-API image layer (the dark state and its source, the palette and whether it
 * recolors the image, the filters, the theme digest), in the rendering snapshot, and for legacy `CachedImageIcon` nodes.
 *
 * Test artwork (iconsTest/theme of the icons tests): `switch.svg`, the left half `#ced0d6`, the right half `#6c707e`.
 */
@TestApplication
class ThemeInspectionTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private var previousPalette: SVGLoader.SvgElementColorPatcherProvider? = null
  private val previousTransform = pathTransform.get()
  private val classLoader: ClassLoader = ThemeInspectionTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    previousPalette = SVGLoader.colorPatcherProvider
    IntelliJIconManager.activate()
    IconLoader.activate()
    SVGLoader.colorPatcherProvider = null
    IconLoader.setUseDarkIcons(false)
    IconLoader.clearCacheInTests()
  }

  @AfterEach
  fun tearDown() {
    SVGLoader.colorPatcherProvider = previousPalette
    pathTransform.set(previousTransform)
    IconLoader.clearCacheInTests()
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `an image layer follows the dark state of the theme`() = runInEdtAndWait {
    assertEquals("false", imageLayerTheme(native())["effective dark"])
    assertEquals("theme (dark=false)", imageLayerTheme(native())["dark source"])

    IconLoader.setUseDarkIcons(true)
    assertEquals("true", imageLayerTheme(native())["effective dark"])
    assertEquals("theme (dark=true)", imageLayerTheme(native())["dark source"])
  }

  @Test
  fun `the dark modifier of an outer layer is the dark source`() = runInEdtAndWait {
    val descriptor = iconDescriptor { icon(imageIconDescriptor(SWITCH_ICON, classLoader), IconModifier.dark(true)) }
    val rows = imageLayerTheme(descriptor.createSwingIcon() as SwingIcon)
    assertEquals("true", rows["effective dark"])
    assertTrue(rows["dark source"]!!.startsWith("dark() modifier on NestedIconLayer"), rows["dark source"])

    val own = imageLayerTheme(imageIconDescriptor(SWITCH_ICON, classLoader, IconModifier.dark(true)).createSwingIcon() as SwingIcon)
    assertEquals("dark() modifier on this layer (dark=true)", own["dark source"])
  }

  @Test
  fun `the getDarkIcon override of a SwingIcon is the dark source`() = runInEdtAndWait {
    val dark = IconLoader.getDarkIcon(native(), true) as SwingIcon
    val rows = imageLayerTheme(dark)
    assertEquals("true", rows["effective dark"])
    assertEquals("SwingIcon getDarkIcon override (dark=true)", rows["dark source"])
  }

  @Test
  fun `the palette check tells whether the palette recolors the image`() = runInEdtAndWait {
    assertEquals("no: the theme has no palette", imageLayerTheme(native())["palette recolors this image"])

    SVGLoader.colorPatcherProvider = palette("#6c707e")
    val recolored = imageLayerTheme(native())
    assertTrue(recolored["palette recolors this image"]!!.startsWith("yes: "), recolored["palette recolors this image"])
    assertTrue(recolored["color palette"]!!.contains("digest 42,4242"), recolored["color palette"])
    assertTrue(recolored["theme digest"]!!.contains("palette=42,4242"), recolored["theme digest"])

    SVGLoader.colorPatcherProvider = palette("#123456")
    assertEquals("no: the image has no palette colors", imageLayerTheme(native())["palette recolors this image"])

    val stroked = imageIconDescriptor(SWITCH_ICON, classLoader, IconModifier.stroke(DefaultSRGB.fromHex("#FF00FF")))
    val strokedRows = imageLayerTheme(stroked.createSwingIcon() as SwingIcon)
    assertEquals("no: a stroked image does not take the palette", strokedRows["palette recolors this image"])
    assertTrue(strokedRows["effective dark"]!!.contains("light artwork"), strokedRows["effective dark"])
  }

  @Test
  fun `the image layer shows the global filter and the color filter modifier`() = runInEdtAndWait {
    assertEquals("none", imageLayerTheme(native())["global filter"])
    assertEquals("none", imageLayerTheme(native())["color filter modifier"])
    IconLoader.setFilter(NoOpFilter())
    assertTrue(imageLayerTheme(native())["global filter"]!!.contains("NoOpFilter"))
  }

  @Test
  fun `the rendering snapshot shows the theme inputs`() = runInEdtAndWait {
    SVGLoader.colorPatcherProvider = palette("#6c707e")
    val icon = IconLoader.getDarkIcon(native(), true) as SwingIcon
    val rows = RenderingSnapshots.properties(icon, icon.iconDescriptor, null)
      .filter { it.group == ThemeInspection.GROUP_THEME }
      .associate { it.name to it.value }
    assertNotNull(rows["theme digest"])
    assertEquals("false", rows["theme dark"])
    assertEquals("true", rows["caller dark override"])
    assertEquals("SwingIcon getDarkIcon override (dark=true)", rows["caller dark source"])
    assertTrue(rows["color palette"]!!.contains("digest 42,4242"), rows["color palette"])
    assertTrue(rows["selection patcher"]!!.endsWith("(not applied by the renderers)"), rows["selection patcher"])
  }

  @Test
  fun `a legacy icon shows its dark state and its color patcher`() {
    val legacy = IconLoader.findIcon(SWITCH_ICON, classLoader) as CachedImageIcon
    val plain = legacyTheme(legacy)
    assertEquals("false", plain["effective dark"])
    assertEquals("theme (IconLoader.setUseDarkIcons)", plain["dark source"])
    assertEquals("none", plain["color patcher"])

    val dark = legacyTheme(legacy.getDarkIcon(true))
    assertEquals("true", dark["effective dark"])
    assertEquals("getDarkIcon override (dark=true)", dark["dark source"])

    SVGLoader.colorPatcherProvider = palette("#6c707e")
    assertTrue(legacyTheme(legacy)["color patcher"]!!.startsWith("theme palette: "), legacyTheme(legacy)["color patcher"])
  }

  private fun native(): SwingIcon = imageIconDescriptor(SWITCH_ICON, classLoader).createSwingIcon() as SwingIcon

  /** The theme rows of the first image layer node of the static tree of [icon]. */
  private fun imageLayerTheme(icon: SwingIcon): Map<String, String?> {
    val root = InspectorTreeBuilder.buildStatic("t:", icon, icon.iconDescriptor).root
    val layer = findImageLayer(root) ?: error("no image layer in the tree of $icon")
    return layer.properties.filter { it.group == ThemeInspection.GROUP_THEME }.associate { it.name to it.value }
  }

  private fun findImageLayer(node: InspectorNode): InspectorNode? {
    if (node.kind == InspectorNodeKind.LAYER && node.title == "ImageIconLayer") return node
    return node.children.firstNotNullOfOrNull(::findImageLayer)
  }

  private fun legacyTheme(icon: Icon): Map<String, String?> =
    LegacyIcons.properties(icon).filter { it.group == ThemeInspection.GROUP_THEME }.associate { it.name to it.value }

  private fun palette(color: String): SVGLoader.SvgElementColorPatcherProvider = object : SVGLoader.SvgElementColorPatcherProvider {
    private val patcher = newSvgPatcher(mapOf(color to "#ff0000"), alphaProvider = { null })
    override fun attributeForPath(path: String) = patcher
    override fun digest(): LongArray = longArrayOf(42, 4242)
  }

  private class NoOpFilter : RGBImageFilter() {
    override fun filterRGB(x: Int, y: Int, rgb: Int): Int = rgb
  }

  private companion object {
    const val SWITCH_ICON = "iconsTest/theme/switch.svg"
  }
}
