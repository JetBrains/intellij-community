// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.icons.AllIcons
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.SVGLoader
import com.intellij.openapi.wm.impl.headertoolbar.isDarkHeader
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import java.awt.Dimension
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.ui.icons.loadRasterizedIcon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JLabel

/**
 * The main toolbar's Search Everywhere icon (`expui/general/search.svg`) enlarged to the toolbar's 20px the way
 * `HeaderToolbarButtonLook.scaleAndAdjustIcon` does it, with the new icons pipeline and with the legacy one, painted at
 * density 2: frame size vs. visible content, and what [PaintRecorder] reports.
 */
@TestApplication
class ToolbarSearchIconSizeTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    IntelliJIconManager.activate()
    IconRenderDebugger.clearAll()
    // legacy icons paint nothing in headless tests unless activated
    IconLoader.activate()
  }

  @AfterEach
  fun tearDown() {
    IconLoader.deactivate()
    IconRenderDebugger.clearAll()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `toolbar search icon painted size, new pipeline vs legacy`() = runInEdtAndWait {
    val classLoader = AllIcons::class.java.classLoader
    // (a) new API: SwingIcon of the descriptor, enlarged through the ScalableIcon bridge (HeaderToolbarButtonLook → scale(20/16))
    val newIcon = imageIconDescriptor(PATH, classLoader).createSwingIcon()
    val newScaled = loadIconCustomVersionOrScale(newIcon as ScalableIcon, 20)
    // (b) legacy: the CachedImageIcon, enlarged the way HeaderToolbarButtonLook does it
    val legacy = IconLoader.getIcon(PATH, classLoader) as CachedImageIcon
    val legacyScaled = loadIconCustomVersionOrScale(legacy, 20)

    val newPaths = imagePaths((newScaled as SwingIcon).iconDescriptor)
    val legacyPath = (legacyScaled as? CachedImageIcon)?.getCoords()?.first
    val newResult = measure("new: SwingIcon scale=${newScaled.scale}, resources $newPaths", newScaled)
    val legacyResult = measure("legacy: ${legacyScaled.javaClass.simpleName}, resource $legacyPath (original ${legacy.getCoords()?.first})",
                               legacyScaled)

    // both frames are 20×20 logical = 40×40 device px; PaintRecorder must report the visible content, not the frame
    assertEquals(newResult.frame, legacyResult.frame)
    assertEquals(newResult.content.width, newResult.recorded!!.width)
    assertEquals(legacyResult.content.width, legacyResult.recorded!!.width)
    // both paths paint the hand-drawn 20px variant: legacy via loadIconCustomVersion, the icons API via the image loader's
    // exact @WxH lookup (the descriptor itself keeps naming the base artwork)
    assertEquals("expui/general/search@20x20.svg", legacyPath)
    assertEquals(listOf(PATH), newPaths)
    assertEquals(legacyResult.content, newResult.content, "legacy ${legacyResult.content} vs new ${newResult.content}")

    // the inspector shows it on the picked root ("Size variant")
    val newRow = SizeVariants.forDescriptor(newScaled.iconDescriptor, 20)!!.value!!
    val legacyRow = SizeVariants.forLegacy(legacy, 20)!!.value!!
    println("[toolbar-search] Size variant (new):    $newRow")
    println("[toolbar-search] Size variant (legacy): $legacyRow")
    assertTrue(newRow.startsWith("expui/general/search@20x20.svg"), newRow)
    assertTrue(legacyRow.startsWith("expui/general/search@20x20.svg"), legacyRow)
  }

  @Test
  fun `resolved svg sources of the toolbar search icon for both pipelines`() = runInEdtAndWait {
    val classLoader = AllIcons::class.java.classLoader
    val button = ActionButton(object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }, Presentation(), "MainToolbar", Dimension(40, 40))
    button.setLook(HeaderToolbarButtonLook())
    val sizing = SizingContext(screenScale = DENSITY.toFloat(), rootPainted = null)
    val picks = listOf(
      "legacy pick" to (IconLoader.getIcon(PATH, classLoader) as CachedImageIcon),
      "new-API pick" to imageIconDescriptor(PATH, classLoader).createSwingIcon(),
    )
    for ((label, icon) in picks) {
      val picked = PickedIcon(button, button, icon, "ActionButton", emptyList(), null, java.awt.Point(10, 10))
      val sources = SvgSources.compute(picked, sizing)
      for (source in sources) {
        println("[svg-sources] $label · ${source.pipeline}")
        println("[svg-sources]   basePath     ${source.basePath}")
        println("[svg-sources]   steps        ${source.steps}")
        println("[svg-sources]   finalPath    ${source.finalPath}")
        println("[svg-sources]   resolution   ${source.resolution}")
        println("[svg-sources]   patches      ${source.patches}")
        println("[svg-sources]   rendering    ${source.rendering?.let { "${it.width}×${it.height}" }}")
        println("[svg-sources]   content      ${source.contentBounds}")
        println("[svg-sources]   svgText      ${source.svgText?.length} chars")
      }
      val legacySource = sources.single { it.pipeline == IconPipeline.LEGACY }
      val newSource = sources.single { it.pipeline == IconPipeline.NEW_ICONS_API }
      assertEquals(PATH, legacySource.basePath, label)
      assertEquals(PATH, newSource.basePath, label)

      // legacy: the look loads the hand-drawn @20x20 variant (dark header → _dark variant if the header is dark)
      val variant = "expui/general/search@20x20.svg"
      assertTrue(legacySource.finalPath == variant || legacySource.finalPath == "expui/general/search@20x20_dark.svg",
                 "$label: ${legacySource.finalPath}")
      assertTrue(legacySource.steps.any { it.startsWith("size variant @20x20") }, "$label: ${legacySource.steps}")
      assertTrue(legacySource.resolution.startsWith("reproduced HeaderToolbarButtonLook"), legacySource.resolution)
      assertNotNull(legacySource.svgText)
      assertEquals("34×34 at (4, 4)", legacySource.contentBounds, label)

      // new API: determined by pixel match; either the scaled base artwork (today) or the variant (with the size-variant fix)
      val newFinal = newSource.finalPath
      assertNotNull(newFinal, "$label: ${newSource.resolution}")
      assertTrue(newSource.resolution.startsWith("pixel match"), newSource.resolution)
      val candidates = setOf(PATH, "expui/general/search_dark.svg", variant, "expui/general/search@20x20_dark.svg")
      assertTrue(newFinal in candidates, newFinal)
      if (newFinal!!.startsWith("expui/general/search@20x20")) {
        assertTrue(newSource.steps.any { it.startsWith("size variant") }, newSource.steps.toString())
        assertEquals(legacySource.contentBounds, newSource.contentBounds)
      }
      else {
        assertTrue(newSource.steps.any { it.startsWith("no size variant") }, newSource.steps.toString())
        assertEquals("30×30 at (5, 5)", newSource.contentBounds, label)
      }
    }
  }

  /**
   * The real main-toolbar chain: `AllIcons.Actions.Find` (classic `actions/find.svg`, mapped to `expui/general/search.svg`
   * under the new UI) → `IconLoader.getDarkIcon(icon, isDarkHeader())` (MainToolbar's HeaderIconUpdater) on an ActionButton
   * with HeaderToolbarButtonLook; for the new pipeline the action's icon is bridged (SwingIconLayer of that legacy icon).
   */
  @Test
  fun `resolved svg sources of the real main toolbar search chain`() = runInEdtAndWait {
    // exactly what AllIcons.Actions.Find is: load("expui/general/search.svg", "actions/find.svg", 1275373899, 2); AllIcons' own
    // static fields are dummies in headless tests (no real legacy IconManager), so load it the same way directly
    val find = loadRasterizedIcon("actions/find.svg", PATH, AllIcons::class.java.classLoader, 1275373899, 2, null) as CachedImageIcon
    val dark = isDarkHeader()
    val held = IconLoader.getDarkIcon(find, dark) as CachedImageIcon
    val bridged = SwingIcon(held.toIconDescriptor())
    val sizing = SizingContext(screenScale = DENSITY.toFloat(), rootPainted = null)
    val expected = if (dark) "expui/general/search@20x20_dark.svg" else "expui/general/search@20x20.svg"
    for ((label, icon) in listOf("icons OFF (CachedImageIcon)" to held, "icons ON (bridged SwingIcon)" to bridged)) {
      val picked = PickedIcon(toolbarButton(), toolbarButton(), icon, "ActionButton", emptyList(), null, java.awt.Point(10, 10))
      val sources = SvgSources.compute(picked, sizing)
      sources.forEach { print("real chain · $label", it) }
      val legacySource = sources.single { it.pipeline == IconPipeline.LEGACY }
      val newSource = sources.single { it.pipeline == IconPipeline.NEW_ICONS_API }

      // never the unmapped classic path
      assertEquals(PATH, legacySource.basePath, label)
      assertEquals(expected, legacySource.finalPath, label)
      assertTrue(legacySource.steps.first().startsWith("theme mapping: actions/find.svg → $PATH"), legacySource.steps.toString())
      assertTrue(legacySource.steps.any { it.startsWith("size variant @20x20") }, legacySource.steps.toString())
      assertEquals("34×34 at (4, 4)", legacySource.contentBounds, label)

      assertEquals(PATH, newSource.basePath, label)
      assertNotNull(newSource.finalPath, newSource.resolution)
      if (icon is SwingIcon) {
        assertTrue(newSource.steps.first().startsWith("bridged: SwingIcon → DefaultLayeredIconDescriptor → SwingIconLayer"),
                   newSource.steps.toString())
      }
      if (newSource.finalPath!!.contains("@20x20")) {
        assertEquals(legacySource.contentBounds, newSource.contentBounds, label)
      }
      else {
        assertTrue(newSource.steps.any { it.startsWith("no size variant") }, newSource.steps.toString())
        assertEquals("30×30 at (5, 5)", newSource.contentBounds, label)
      }
    }
  }

  /**
   * Closest to the real IDE: the theme's SVG color palette is active (as `LafManager` installs it via
   * `SVGLoader.colorPatcherProvider`), the icon is `AllIcons.Actions.Find` made dark by HeaderIconUpdater, and the button
   * holds either the legacy icon (experimental icons OFF), a bridged SwingIcon (Presentation's write bridge), or the native
   * descriptor (`AllIconDescriptors.Actions.Find` = imageIconDescriptor("expui/general/search.svg")).
   */
  @Test
  fun `resolved svg sources of the real-like main toolbar chain with theme color patching`() = runInEdtAndWait {
    val previousPatcher = SVGLoader.colorPatcherProvider
    SVGLoader.colorPatcherProvider = PALETTE
    try {
      val find = loadRasterizedIcon("actions/find.svg", PATH, AllIcons::class.java.classLoader, 1275373899, 2, null) as CachedImageIcon
      val held = IconLoader.getDarkIcon(find, isDarkHeader()) as CachedImageIcon
      val picks = listOf(
        "icons OFF (CachedImageIcon)" to (held as Icon),
        "icons ON bridged (SwingIcon of SwingIconLayer)" to SwingIcon(held.toIconDescriptor()),
        "icons ON native (AllIconDescriptors.Actions.Find)" to imageIconDescriptor(PATH, AllIcons::class.java.classLoader).createSwingIcon(),
      )
      val sizing = SizingContext(screenScale = DENSITY.toFloat(), rootPainted = null)
      for ((label, icon) in picks) {
        val picked = PickedIcon(toolbarButton(), toolbarButton(), icon, "ActionButton", emptyList(), null, java.awt.Point(10, 10))
        val sources = SvgSources.compute(picked, sizing)
        sources.forEach { print("real-like · $label", it) }
        val newSource = sources.single { it.pipeline == IconPipeline.NEW_ICONS_API }
        val legacySource = sources.single { it.pipeline == IconPipeline.LEGACY }
        assertTrue(legacySource.finalPath!!.startsWith("expui/general/search@20x20"), "$label: ${legacySource.finalPath}")
        assertNotNull(newSource.finalPath, "$label: ${newSource.resolution}")
        assertTrue(newSource.finalPath!!.startsWith("expui/general/search@20x20"), "$label: ${newSource.finalPath}")
        assertEquals("34×34 at (4, 4)", newSource.contentBounds, label)
        assertTrue(newSource.steps.isNotEmpty(), label)
      }
    }
    finally {
      SVGLoader.colorPatcherProvider = previousPatcher
    }
  }

  private fun toolbarButton(): ActionButton {
    val button = ActionButton(object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }, Presentation(), "MainToolbar", Dimension(40, 40))
    button.setLook(HeaderToolbarButtonLook())
    return button
  }

  private fun print(label: String, source: ResolvedSvgSource) {
    println("[svg-sources] $label · ${source.pipeline}")
    println("[svg-sources]   basePath     ${source.basePath}")
    println("[svg-sources]   steps        ${source.steps}")
    println("[svg-sources]   finalPath    ${source.finalPath}")
    println("[svg-sources]   resolution   ${source.resolution}")
    println("[svg-sources]   patches      ${source.patches}")
    println("[svg-sources]   rendering    ${source.rendering?.let { "${it.width}×${it.height}" }}")
    println("[svg-sources]   content      ${source.contentBounds}")
    println("[svg-sources]   svgText      ${source.svgText?.length} chars")
  }

  private fun imagePaths(descriptor: IconDescriptor): List<String> {
    val result = ArrayList<String>()
    fun visitLayer(layer: IconLayer) {
      when (layer) {
        is ImageIconLayer -> result.add((layer.loader as? ModuleImageResourceLocation)?.path ?: layer.loader.toString())
        is NestedIconLayer -> visit(layer.iconDescriptor)
        is LayoutIconLayer -> layer.nestedLayers.forEach(::visitLayer)
        else -> Unit
      }
    }
    fun visitDescriptor(d: IconDescriptor) {
      (d as? DefaultLayeredIconDescriptor)?.layers?.forEach(::visitLayer)
    }
    visit = ::visitDescriptor
    visitDescriptor(descriptor)
    return result
  }

  private var visit: (IconDescriptor) -> Unit = {}

  private class Result(val frame: Rectangle, val content: Rectangle, val recorded: SizeValue?)

  private fun measure(label: String, icon: Icon): Result {
    val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(DENSITY, DENSITY)
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    val frame = Rectangle(0, 0, (icon.iconWidth * DENSITY).toInt(), (icon.iconHeight * DENSITY).toInt())
    val any = alphaBounds(image, 0)
    val solid = alphaBounds(image, 25)
    assertNotNull(any, "$label painted nothing")

    val component = object : JLabel() {
      override fun paintComponent(g: java.awt.Graphics) = icon.paintIcon(this, g, 0, 0)
    }
    component.setSize(icon.iconWidth, icon.iconHeight)
    val draws = PaintRecorder.record(component, DENSITY.toFloat())
    val recorded = PaintRecorder.choose(draws, java.awt.Point(icon.iconWidth / 2, icon.iconHeight / 2), DENSITY.toFloat())

    println("[toolbar-search] $label")
    println("[toolbar-search]   icon size (logical)   ${icon.iconWidth}×${icon.iconHeight}, " +
            "frame (device) ${frame.width}×${frame.height}")
    println("[toolbar-search]   content alpha>0       ${any!!.width}×${any.height} at (${any.x}, ${any.y})")
    println("[toolbar-search]   content alpha>25      ${solid?.width}×${solid?.height} at (${solid?.x}, ${solid?.y})")
    val drawText = draws.joinToString { draw ->
      val b = draw.bounds
      "${b.width}×${b.height}@(${b.x},${b.y})" + (draw.content?.let { c -> " content ${c.width}×${c.height}@(${c.x},${c.y})" } ?: "")
    }
    println("[toolbar-search]   draws                 $drawText")
    println("[toolbar-search]   PaintRecorder painted ${recorded?.width}×${recorded?.height} ${recorded?.provenance} — " +
            "${recorded?.note}")
    return Result(frame, any, recorded)
  }

  private fun alphaBounds(image: BufferedImage, threshold: Int): Rectangle? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if ((image.getRGB(x, y) ushr 24) > threshold) {
          if (x < minX) minX = x
          if (y < minY) minY = y
          if (x > maxX) maxX = x
          if (y > maxY) maxY = y
        }
      }
    }
    return if (maxX < 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
  }

  private companion object {
    /** A theme-like palette: the icon gray becomes red (light) / green (dark). */
    val PALETTE = object : SVGLoader.SvgElementColorPatcherProvider {
      private val patcher = newSvgPatcher(mapOf("#6c707e" to "#ff0000", "#ced0d6" to "#00ff00"), alphaProvider = { null })
      override fun attributeForPath(path: String) = patcher
      override fun digest(): LongArray = longArrayOf(42, 4242)
    }

    const val PATH = "expui/general/search.svg"
    const val DENSITY = 2.0
    const val SIZE = 128
  }
}
