// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.modifiers.AlphaIconModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.MarginIconModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.alpha
import com.intellij.platform.icons.modifiers.margin
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.scale.factor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.util.IdentityHashMap
import javax.swing.JLabel

@TestApplication
class InspectorTreeBuilderTest {
  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
  }

  @Test
  fun `combined modifiers are flattened in application order without root modifiers`() {
    testIcons {
      val scale = ScaleModifier(factor(1.25f))
      val margin = MarginIconModifier(1.px, 2.px, 3.px, 4.px)
      val alpha = AlphaIconModifier(0.5f)
      val modifier = CombinedIconModifier(CombinedIconModifier(CombinedIconModifier(RootIconModifier, scale), margin), alpha)
      assertEquals(listOf(scale, margin, alpha), InspectorTreeBuilder.flattenModifiers(modifier))
      assertEquals(emptyList<IconModifier>(), InspectorTreeBuilder.flattenModifiers(IconModifier))
      assertEquals(emptyList<IconModifier>(), InspectorTreeBuilder.flattenModifiers(RootIconModifier))
    }
  }

  @Test
  fun `modifier nodes follow application order and show their fields`() {
    testIcons {
      val image = testImage(16, 16)
      val descriptor = iconDescriptor { image(image, IconModifier.scale(factor(1.25f)).margin(2.px).alpha(0.5f)) }
      val root = InspectorTreeBuilder.buildStatic("t:", null, descriptor).root

      val layer = root.children.single().children.single()
      assertEquals(InspectorNodeKind.LAYER, layer.kind)
      assertEquals("ImageIconLayer", layer.title)
      val modifiers = layer.children.filter { it.kind == InspectorNodeKind.MODIFIER }
      assertEquals(listOf("ScaleModifier", "MarginIconModifier", "AlphaIconModifier"), modifiers.map { it.title })
      assertEquals("1.25", modifiers[0].property("factor"))
      assertEquals("factor=1.25", modifiers[0].subtitle)
      assertEquals("2px", modifiers[1].property("left"))
      assertEquals("0.5", modifiers[2].property("alpha"))
      // the resource node comes after the modifiers
      assertEquals(InspectorNodeKind.RESOURCE, layer.children.last().kind)
      assertTrue(layer.children.all { it.origin == InspectorNodeOrigin.ICONS_API })
      assertEquals(InspectorNodeOrigin.ICONS_API, layer.origin)
    }
  }

  @Test
  fun `image resource node shows all location fields`() {
    IntelliJIconManager() // registers the ModuleImageResourceLocation describer
    testIcons {
      run {
        val location = ModuleImageResourceLocation("expui/general/save.svg", "com.intellij", "intellij.platform.ide")
        val descriptor = DefaultLayeredIconDescriptor(listOf(ImageIconLayer(location, IconModifier)))
        val layer = InspectorTreeBuilder.buildStatic("t:", null, descriptor).root.children.single().children.single()
        assertEquals("expui/general/save.svg", layer.subtitle)
        val resource = layer.children.single()
        assertEquals(InspectorNodeKind.RESOURCE, resource.kind)
        assertEquals("ModuleImageResourceLocation", resource.title)
        assertEquals("expui/general/save.svg", resource.subtitle)
        assertEquals("expui/general/save.svg", resource.property("path"))
        assertEquals("com.intellij", resource.property("pluginId"))
        assertEquals("intellij.platform.ide", resource.property("moduleId"))
      }
    }
  }

  @Test
  fun `long chain of nested wrappers is reported on the top wrapper only`() {
    testIcons {
      val root = InspectorTreeBuilder.buildStatic("t:", null, wrap(iconDescriptor { image(testImage(16, 16)) }, 5)).root
      val wrappers = generateSequence(root.children.single().children.single()) { node ->
        node.children.singleOrNull { it.kind == InspectorNodeKind.ICON }?.children?.singleOrNull { it.title == "NestedIconLayer" }
      }.toList()
      assertEquals(5, wrappers.size)
      assertNotNull(wrappers.first().warning)
      assertTrue(wrappers.first().warning!!.contains("5"), wrappers.first().warning)
      assertTrue(wrappers.drop(1).all { it.warning == null })
      assertEquals(5, InspectorTreeBuilder.wrapperChainLength(
        (wrap(iconDescriptor { image(testImage(16, 16)) }, 5) as DefaultLayeredIconDescriptor).layers.single() as NestedIconLayer))
    }
  }

  @Test
  fun `short chain of nested wrappers is not reported`() {
    testIcons {
      val root = InspectorTreeBuilder.buildStatic("t:", null, wrap(iconDescriptor { image(testImage(16, 16)) }, 3)).root
      assertTrue(root.allNodes().none { it.warning != null })
    }
  }

  @Test
  fun `static tree is not debuggable`() {
    testIcons {
      val descriptor = iconDescriptor {
        icon(iconDescriptor { image(testImage(8, 8)) })
        row {
          image(testImage(4, 4))
          image(testImage(6, 6))
        }
      }
      val tree = InspectorTreeBuilder.buildStatic("t:", SwingIcon(descriptor), descriptor)
      assertTrue(tree.renderers.isEmpty())
      val nodes = tree.root.allNodes()
      assertTrue(nodes.none { it.debuggable })
      assertEquals(InspectorNodeKind.SWING_ICON, tree.root.kind)
      assertEquals("SwingIcon", tree.root.title)
      assertEquals(InspectorNodeOrigin.BRIDGE, tree.root.origin)
      assertTrue(nodes.drop(1).all { it.origin == InspectorNodeOrigin.ICONS_API })
      assertEquals(listOf("NestedIconLayer", "LayoutIconLayer"), tree.root.children.single().children.map { it.title })
      // ids are unique
      assertEquals(nodes.size, nodes.map { it.id }.toSet().size)
    }
  }

  @Test
  fun `capture tree maps node ids to the renderer instances that painted`() {
    testIcons {
      val imageA = testImage(4, 4)
      val imageB = testImage(6, 6)
      val child = iconDescriptor { image(testImage(8, 8), IconModifier.scale(factor(2f))) }
      val descriptor = iconDescriptor {
        icon(child)
        row {
          image(imageA)
          image(imageB)
        }
      }
      val icon = SwingIcon(descriptor)
      val component = JLabel()
      val capture = capture(icon, component)

      val tree = InspectorTreeBuilder.buildFromCapture("c:", icon, capture)

      val renderNodes = capture.allRenderNodes()
      val renderersBySubject = IdentityHashMap<Any, RenderNode>()
      renderNodes.forEach { node -> node.subject?.let { renderersBySubject[it] = node } }

      val nodes = tree.root.allNodes()
      val debuggable = nodes.filter { it.debuggable }
      // every renderer invocation appears exactly once as a debuggable node
      assertEquals(renderNodes.size, debuggable.size)
      assertEquals(debuggable.map { it.id }.toSet(), tree.renderers.keys)
      val mapped = tree.renderers.values.toList()
      assertEquals(renderNodes.size, mapped.toSet().size)
      assertTrue(renderNodes.all { node -> mapped.any { it === node.renderer } })

      val iconNode = tree.root.children.single()
      assertSame(capture.iconRenderer, tree.renderers[iconNode.id])
      assertTrue(iconNode.properties.any { it.group == InspectorTreeBuilder.GROUP_SCALING && it.name == "display density" })

      val (nestedLayer, layoutLayer) = iconNode.children
      val descriptorLayers = (descriptor as DefaultLayeredIconDescriptor).layers
      assertSame(renderersBySubject[descriptorLayers[0]]!!.renderer, tree.renderers[nestedLayer.id])
      assertSame(renderersBySubject[descriptorLayers[1]]!!.renderer, tree.renderers[layoutLayer.id])
      val childIconNode = nestedLayer.children.single { it.kind == InspectorNodeKind.ICON }
      assertSame(renderersBySubject[child]!!.renderer, tree.renderers[childIconNode.id])

      val imageNodes = layoutLayer.children.filter { it.title == "ImageIconLayer" }
      assertEquals(2, imageNodes.size)
      val layoutLayers = (descriptorLayers[1] as LayoutIconLayer).nestedLayers
      assertSame(renderersBySubject[layoutLayers[0]]!!.renderer, tree.renderers[imageNodes[0].id])
      assertSame(renderersBySubject[layoutLayers[1]]!!.renderer, tree.renderers[imageNodes[1].id])

      // modifiers and resources are not renderers
      assertTrue(nodes.filter { it.kind == InspectorNodeKind.MODIFIER || it.kind == InspectorNodeKind.RESOURCE }.none { it.debuggable })
      val scale = childIconNode.children.single().children.first { it.kind == InspectorNodeKind.MODIFIER }
      assertEquals("2.0", scale.property("factor"))

      // flagging the instance behind a node id hits exactly that renderer
      val target = tree.renderers.getValue(imageNodes[1].id)
      IconRenderDebugger.flag(target)
      paint(icon, component)
      assertEquals(1, IconRenderDebugger.hitCount(target))
      assertTrue(tree.renderers.values.filter { it !== target }.none { IconRenderDebugger.hitCount(it) > 0 })
    }
  }

  @Test
  fun `capture rebuild yields the same ids`() {
    testIcons {
      val descriptor = iconDescriptor { row { image(testImage(4, 4)); image(testImage(6, 6)) } }
      val icon = SwingIcon(descriptor)
      val component = JLabel()
      val first = InspectorTreeBuilder.buildFromCapture("c:", icon, capture(icon, component))
      val second = InspectorTreeBuilder.buildFromCapture("c:", icon, capture(icon, component))
      assertEquals(first.renderers.keys, second.renderers.keys)
      for ((id, renderer) in first.renderers) assertSame(renderer, second.renderers[id])
      assertNull(first.root.warning)
      assertFalse(first.renderers.isEmpty())
    }
  }

  private fun wrap(descriptor: IconDescriptor, times: Int): IconDescriptor {
    var current = descriptor
    repeat(times) { current = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(current, IconModifier))) }
    return current
  }

  private fun capture(icon: SwingIcon, component: JLabel): RenderCapture {
    val captures = mutableListOf<RenderCapture>()
    IconRenderDebugger.capture({ it === component }) { captures.add(it) }.use { paint(icon, component) }
    return captures.single()
  }

  private fun paint(icon: SwingIcon, component: JLabel) {
    val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      icon.paintIcon(component, graphics, 0, 0)
    }
    finally {
      graphics.dispose()
    }
  }

  private fun RenderCapture.allRenderNodes(): List<RenderNode> {
    val result = mutableListOf<RenderNode>()
    fun visit(node: RenderNode) {
      result.add(node)
      node.children.forEach(::visit)
    }
    roots.forEach(::visit)
    return result
  }

  private fun InspectorNode.allNodes(): List<InspectorNode> = listOf(this) + children.flatMap { it.allNodes() }

  private fun InspectorNode.property(name: String): String? = properties.firstOrNull { it.name == name }?.value
}
