// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.design.rectangle
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.ReplaceableIcon
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel

/** Swing layers and picked legacy icons, with the real IntelliJ icon managers. */
@TestApplication
class SwingIconLayerCaptureTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null

  @TempDir
  lateinit var tempDir: Path

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    IntelliJIconManager.activate()
    IconRenderDebugger.clearAll()
  }

  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `plain legacy icon in a swing layer`() = runInEdtAndWait {
    val legacy = PlainIcon(10, 10)
    val descriptor = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier)))
    val icon = SwingIcon(descriptor)
    val (capture, tree) = paintAndBuild(icon)

    assertSame(descriptor, capture.descriptor)
    assertEquals(InspectorNodeOrigin.BRIDGE, tree.root.origin)
    val iconNode = tree.root.children.single()
    assertEquals("DefaultLayeredIconDescriptor", iconNode.title)
    assertEquals(InspectorNodeOrigin.ICONS_API, iconNode.origin)
    val layerNode = iconNode.children.single()
    assertEquals("SwingIconLayer", layerNode.title)
    assertEquals(InspectorNodeOrigin.BRIDGE, layerNode.origin)
    assertTrue(layerNode.debuggable)
    val legacyNode = layerNode.children.single()
    assertEquals("PlainIcon", legacyNode.title)
    assertEquals(InspectorNodeOrigin.LEGACY, legacyNode.origin)
    assertTrue(legacyNode.children.isEmpty())
  }

  @Test
  fun `legacy composite containing a SwingIcon in a swing layer`() = runInEdtAndWait {
    val innerDescriptor = iconDescriptor { shape(Color.Black, rectangle(8.px, 8.px)) }
    val inner = SwingIcon(innerDescriptor)
    val legacy = RowIcon(inner, PlainIcon(6, 6))
    val descriptor = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier)))
    val icon = SwingIcon(descriptor)
    val (capture, tree) = paintAndBuild(icon)

    // the capture has a single root: the outer renderer; the inner SwingIcon renders under the swing layer renderer
    val root = capture.roots.single()
    val swingLayerRender = root.children.single()
    assertTrue(swingLayerRender.subject is SwingIconLayer, dump(capture))
    val innerRender = swingLayerRender.children.single { it.subject === innerDescriptor }

    val iconNode = tree.root.children.single()
    val layerNode = iconNode.children.single()
    assertEquals("SwingIconLayer", layerNode.title, dump(tree.root))
    // SwingIconLayer → RowIcon (legacy) → [SwingIcon (bridge) → descriptor (debuggable), PlainIcon (legacy)]
    val rowNode = layerNode.children.single()
    assertEquals("RowIcon", rowNode.title)
    assertEquals(InspectorNodeOrigin.LEGACY, rowNode.origin)
    val (swingIconNode, plainNode) = rowNode.children
    assertEquals("SwingIcon", swingIconNode.title)
    assertEquals(InspectorNodeOrigin.BRIDGE, swingIconNode.origin)
    assertEquals(InspectorNodeOrigin.LEGACY, plainNode.origin)
    val innerIconNode = swingIconNode.children.single()
    assertTrue(innerIconNode.debuggable)
    assertSame(innerRender.renderer, tree.renderers[innerIconNode.id])
    assertTrue(innerIconNode.children.single().debuggable)
    assertEquals(InspectorNodeOrigin.ICONS_API, innerIconNode.children.single().origin)
  }

  @Test
  fun `picked legacy deferred icon shows its legacy structure without the synthetic descriptor`() = runInEdtAndWait {
    val innerDescriptor = iconDescriptor { shape(Color.Black, rectangle(8.px, 8.px)) }
    val inner = SwingIcon(innerDescriptor)
    val cached = cachedImageIcon()
    val layered = LayeredIcon(cached, inner)
    val deferred = TestDeferredIcon(EmptyIcon.create(16), layered)
    val bridged = deferred.toIconDescriptor()
    assertTrue(InspectorTreeBuilder.isTrivialBridge(bridged, deferred))

    // static
    val static = InspectorTreeBuilder.buildStatic("s:", deferred, bridged)
    println("---- static legacy tree ----\n" + dump(static.root))
    assertLegacyShape(static.root, deferred)
    assertTrue(static.renderers.isEmpty())
    assertTrue(static.root.allNodes().none { it.debuggable })

    // captured: the legacy icon itself is painted by Swing, the nested SwingIcon through SwingIcon.paintIcon
    val component = JLabel()
    paintLegacy(deferred, component)
    val captures = mutableListOf<RenderCapture>()
    IconRenderDebugger.capture({ it === component }) { captures.add(it) }.use { paintLegacy(deferred, component) }
    println("---- RenderCapture (legacy root) ----\n" + captures.joinToString("") { dump(it) })
    assertSame(innerDescriptor, captures.single().descriptor)

    val tree = InspectorTreeBuilder.buildForLegacy("c:", deferred, captures, bridged)
    println("---- captured legacy tree ----\n" + dump(tree.root))
    assertLegacyShape(tree.root, deferred)
    val bridge = tree.root.allNodes().single { it.origin == InspectorNodeOrigin.BRIDGE }
    val descriptorNode = bridge.children.single()
    assertTrue(descriptorNode.debuggable)
    assertSame(captures.single().iconRenderer, tree.renderers[descriptorNode.id])
    assertEquals(listOf(innerDescriptor), InspectorTreeBuilder.collectSwingIcons(deferred).map { it.iconDescriptor })
  }

  @Test
  fun `swing layer wrapping the picked SwingIcon itself is a cycle, not a recursion`() = runInEdtAndWait {
    val holder = arrayOfNulls<Icon>(1)
    val wrapper = object : Icon, RetrievableIcon {
      override fun retrieveIcon(): Icon = holder[0]!!
      override fun replaceBy(replacer: com.intellij.ui.icons.IconReplacer): Icon = this
      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {}
      override fun getIconWidth(): Int = 16
      override fun getIconHeight(): Int = 16
    }
    val icon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(wrapper, IconModifier))))
    holder[0] = icon
    val tree = InspectorTreeBuilder.buildStatic("s:", icon, icon.iconDescriptor)
    println("---- cycle tree ----\n" + dump(tree.root))
    val cycle = tree.root.allNodes().single { it.warning != null }
    assertEquals("SwingIcon", cycle.title)
    assertTrue(tree.root.allNodes().size < 10)
  }

  private fun assertLegacyShape(root: InspectorNode, deferred: Icon) {
    // root: the legacy icon itself, no synthetic DefaultLayeredIconDescriptor/SwingIconLayer child
    assertEquals(InspectorNodeKind.SWING_ICON, root.kind)
    assertEquals(InspectorNodeOrigin.LEGACY, root.origin)
    assertEquals(deferred.javaClass.simpleName, root.title)
    assertTrue(root.allNodes().none { it.title == "SwingIconLayer" || it.title == "DefaultLayeredIconDescriptor" && it.origin != InspectorNodeOrigin.ICONS_API },
               dump(root))
    assertTrue(root.allNodes().none { it.warning != null }, dump(root))
    // DeferredIcon → [evaluated: LayeredIcon, base: EmptyIcon]
    val (evaluated, base) = root.children
    assertEquals("LayeredIcon", evaluated.title)
    assertTrue(evaluated.subtitle!!.startsWith("evaluated"), evaluated.subtitle)
    assertEquals(InspectorNodeOrigin.LEGACY, evaluated.origin)
    assertTrue(base.subtitle!!.startsWith("base"), base.subtitle)
    assertEquals(InspectorNodeOrigin.LEGACY, base.origin)
    // LayeredIcon → [layer 0: CachedImageIcon (path), layer 1: SwingIcon (bridge) → descriptor]
    val (cachedNode, swingNode) = evaluated.children
    assertEquals("CachedImageIcon", cachedNode.title)
    assertEquals(InspectorNodeOrigin.LEGACY, cachedNode.origin)
    assertTrue(cachedNode.properties.any { it.name == "original path" && it.value!!.endsWith("test-icon.svg") }, dump(root))
    assertEquals("SwingIcon", swingNode.title)
    assertEquals(InspectorNodeOrigin.BRIDGE, swingNode.origin)
    val descriptorNode = swingNode.children.single()
    assertEquals("DefaultLayeredIconDescriptor", descriptorNode.title)
    assertEquals(InspectorNodeOrigin.ICONS_API, descriptorNode.origin)
    assertEquals(InspectorNodeOrigin.ICONS_API, descriptorNode.children.single().origin)
  }

  private fun cachedImageIcon(): CachedImageIcon {
    val file = tempDir.resolve("test-icon.svg")
    Files.writeString(file, """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="#3574F0"/></svg>""")
    return CachedImageIcon(file.toUri().toURL())
  }

  private fun paintAndBuild(icon: SwingIcon): Pair<RenderCapture, InspectorTree> {
    val component = JLabel()
    // first paint outside any capture, as in the IDE: caches are warm by the time the inspector captures
    paint(icon, component)
    val captures = mutableListOf<RenderCapture>()
    IconRenderDebugger.capture({ it === component }) { captures.add(it) }.use { paint(icon, component) }
    val capture = captures.single()
    val tree = InspectorTreeBuilder.buildFromCapture("c:", icon, capture)
    println("---- RenderCapture ----\n" + dump(capture))
    println("---- InspectorNode tree ----\n" + dump(tree.root))
    return capture to tree
  }

  private fun paint(icon: SwingIcon, component: Component) = paintLegacy(icon, component)

  private fun paintLegacy(icon: Icon, component: Component) {
    val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      icon.paintIcon(component, graphics, 0, 0)
    }
    finally {
      graphics.dispose()
    }
  }

  private class PlainIcon(private val width: Int, private val height: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      g.color = java.awt.Color.RED
      g.fillRect(x, y, width, height)
    }

    override fun getIconWidth(): Int = width
    override fun getIconHeight(): Int = height
    override fun toString(): String = "PlainIcon(${width}x$height)"
  }

  /** An already evaluated deferred icon, like `DeferredIconImpl` after evaluation (without its services). */
  private class TestDeferredIcon(override val baseIcon: Icon, private val evaluated: Icon) : DeferredIcon, RetrievableIcon, ReplaceableIcon {
    override val isDone: Boolean get() = true
    override fun evaluate(): Icon = error("the inspector must not evaluate deferred icons")
    override fun retrieveIcon(): Icon = evaluated
    override fun replaceBy(replacer: com.intellij.ui.icons.IconReplacer): Icon = this
    override fun notifyPaint(c: Component, x: Int, y: Int) {}
    override fun getModificationCount(): Long = 0
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) = evaluated.paintIcon(c, g, x, y)
    override fun getIconWidth(): Int = evaluated.iconWidth
    override fun getIconHeight(): Int = evaluated.iconHeight
  }
}

internal fun dump(capture: RenderCapture): String = buildString {
  fun visit(node: RenderNode, indent: Int) {
    append("  ".repeat(indent)).append(node.renderer.javaClass.simpleName).append(" <- ")
      .append(node.subject?.javaClass?.simpleName).append('\n')
    node.children.forEach { visit(it, indent + 1) }
  }
  append("roots=").append(capture.roots.size).append('\n')
  capture.roots.forEach { visit(it, 1) }
}

internal fun dump(node: InspectorNode): String = buildString {
  fun visit(current: InspectorNode, indent: Int) {
    append("  ".repeat(indent)).append('[').append(current.kind).append('/').append(current.origin).append("] ")
      .append(current.title)
    current.subtitle?.let { append(" — ").append(it) }
    if (current.debuggable) append(" (debuggable)")
    current.warning?.let { append(" !").append(it) }
    append('\n')
    current.children.forEach { visit(it, indent + 1) }
  }
  visit(node, 0)
}

internal fun InspectorNode.allNodes(): List<InspectorNode> = listOf(this) + children.flatMap { it.allNodes() }
