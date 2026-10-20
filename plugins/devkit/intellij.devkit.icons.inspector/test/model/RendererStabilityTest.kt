// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.image.BufferedImage
import java.lang.reflect.Proxy
import javax.swing.Icon
import javax.swing.JLabel

@TestApplication
class RendererStabilityTest {
  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
  }

  // ---- tracker ----

  @Test
  fun `a new renderer instance at the same tree position counts as a recreation`() {
    testIcons {
      val component = JLabel()
      val image = testImage(16, 16)
      val first = SwingIcon(iconDescriptor { image(image) })
      // an equal descriptor in a new SwingIcon: same tree, all renderer instances new
      val second = SwingIcon(iconDescriptor { image(image) })
      val tracker = RendererStabilityTracker()

      val captureA = capture(first, component)
      tracker.onPaint(captureA.iconRenderer)
      val treeA = InspectorTreeBuilder.buildFromCapture("s:", first, captureA)
      assertTrue(tracker.nodeRecreations(treeA.renderers).isEmpty())

      val captureB = capture(second, component)
      tracker.onPaint(captureB.iconRenderer)
      val treeB = InspectorTreeBuilder.buildFromCapture("s:", second, captureB)
      assertEquals(treeA.renderers.keys, treeB.renderers.keys)
      val counts = tracker.nodeRecreations(treeB.renderers)

      assertEquals(treeB.renderers.keys, counts.keys)
      assertTrue(counts.values.all { it == 1 })
      val root = withRecreations(treeB.root, counts)
      val iconNode = root.children.single()
      assertEquals(1, iconNode.recreations)
      assertEquals(1, iconNode.children.single { it.debuggable }.recreations)
      // nodes without a renderer (the Swing icon root, resources) are never "recreated"
      assertEquals(0, root.recreations)

      val stability = tracker.snapshot()
      assertEquals(2, stability.paints)
      assertEquals(1, stability.recreations)
      assertEquals(2, stability.distinctInstances)
      assertNotNull(stability.lastRecreationAgoMs)
    }
  }

  @Test
  fun `repaints by the same renderer instances are stable`() {
    testIcons {
      val component = JLabel()
      val icon = SwingIcon(iconDescriptor { row { image(testImage(4, 4)); image(testImage(6, 6)) } })
      val tracker = RendererStabilityTracker()
      repeat(3) {
        val capture = capture(icon, component)
        tracker.onPaint(capture.iconRenderer)
        val counts = tracker.nodeRecreations(InspectorTreeBuilder.buildFromCapture("s:", icon, capture).renderers)
        assertTrue(counts.isEmpty())
      }
      val stability = tracker.snapshot()
      assertEquals(3, stability.paints)
      assertEquals(0, stability.recreations)
      assertEquals(1, stability.distinctInstances)
      assertNull(stability.lastRecreationAgoMs)
    }
  }

  @Test
  fun `reset zeroes the counters but still notices the next replacement`() {
    var now = 1000L
    val tracker = RendererStabilityTracker { now }
    val a = Any()
    val b = Any()
    tracker.onPaint(a)
    tracker.onPaint(b)
    assertTrue(tracker.nodeRecreations(mapOf("n" to a)).isEmpty())
    assertEquals(mapOf("n" to 1), tracker.nodeRecreations(mapOf("n" to b)))
    now = 5000L
    tracker.reset()
    assertEquals(RendererStability(0, 0, 0, null, 0), tracker.snapshot())
    assertTrue(tracker.currentNodeCounts(listOf("n")).isEmpty())

    now = 6000L
    tracker.onPaint(a)
    assertEquals(mapOf("n" to 1), tracker.nodeRecreations(mapOf("n" to a)))
    now = 6500L
    val stability = tracker.snapshot()
    assertEquals(1, stability.paints)
    assertEquals(1, stability.recreations)
    assertEquals(1500L, stability.observedMs)
    assertEquals(500L, stability.lastRecreationAgoMs)
  }

  // ---- controller ----

  @Test
  fun `controller publishes stability, per-node recreations and replaced flags`() {
    testIcons {
      val scope = CoroutineScope(SupervisorJob())
      val controller = IconInspectorControllerImpl(unusedProject(), scope)
      try {
        val component = JLabel()
        val first = SwingIcon(iconDescriptor { image(testImage(16, 16)) })
        runInEdtAndWait { controller.inspect(picked(component, first), newGeneration = true) }
        // waiting for the first paint: measuring
        assertEquals(RendererStability(0, 0, 0, null, 0), controller.target().stability)

        runInEdtAndWait { paint(first, component) }
        val captured = controller.awaitTarget { target ->
          target.stability?.paints == 1 && target.root.children.singleOrNull()?.debuggable == true
        }
        assertEquals(0, captured.stability!!.recreations)
        val iconNode = captured.root.children.single()
        val layerNode = iconNode.children.first { it.debuggable }
        runInEdtAndWait { controller.setFlagged(layerNode.id, true) }
        assertTrue(runInEdtAndGet { controller.isFlagged(layerNode.id) })

        // the component paints a different SwingIcon at the same place: the icon was recreated
        val second = SwingIcon(iconDescriptor { image(testImage(16, 16)) })
        runInEdtAndWait { paint(second, component) }
        val recreated = controller.awaitTarget { it.stability?.paints == 2 }
        assertEquals(1, recreated.stability!!.recreations)
        assertEquals(2, recreated.stability!!.distinctInstances)
        val recreatedIcon = recreated.root.children.single()
        assertEquals(1, recreatedIcon.recreations)
        assertEquals(1, recreatedIcon.children.first { it.id == layerNode.id }.recreations)
        // the flag still holds the old instance, which no longer paints
        assertFalse(runInEdtAndGet { controller.isFlagged(layerNode.id) })
        val flagged = controller.flagged.value.single()
        assertTrue(flagged.title.endsWith(DevkitIconsInspectorBundle.message("model.flag.replaced")), flagged.title)

        // re-flagging moves the flag to the current instance
        runInEdtAndWait { controller.setFlagged(layerNode.id, true) }
        assertTrue(runInEdtAndGet { controller.isFlagged(layerNode.id) })
        assertFalse(controller.flagged.value.single().title.endsWith(DevkitIconsInspectorBundle.message("model.flag.replaced")))

        runInEdtAndWait { paint(second, component) }
        val stable = controller.awaitTarget { it.stability?.paints == 3 }
        assertEquals(1, stable.stability!!.recreations)

        runInEdtAndWait { controller.resetStability() }
        val reset = controller.target()
        assertEquals(0, reset.stability!!.paints)
        assertEquals(0, reset.stability!!.recreations)
        assertEquals(0, reset.root.children.single().recreations)
      }
      finally {
        runInEdtAndWait {
          controller.clearFlags()
          controller.dispose()
        }
        scope.cancel()
      }
    }
  }

  @Test
  fun `legacy picks have no stability`() {
    testIcons {
      val scope = CoroutineScope(SupervisorJob())
      val controller = IconInspectorControllerImpl(unusedProject(), scope)
      try {
        runInEdtAndWait { controller.inspect(picked(JLabel(), EmptyIcon.create(16)), newGeneration = true) }
        assertNull(controller.target().stability)
      }
      finally {
        runInEdtAndWait { controller.dispose() }
        scope.cancel()
      }
    }
  }

  /** The controller does not use its project. */
  private fun unusedProject(): Project =
    Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, _, _ -> null } as Project

  private fun picked(component: JLabel, icon: Icon) =
    PickedIcon(component, component, icon, "JLabel", emptyList(), null, Point(0, 0))

  private fun IconInspectorControllerImpl.target(): InspectionTarget =
    (state.value as InspectorState.Inspecting).target

  private fun IconInspectorControllerImpl.awaitTarget(predicate: (InspectionTarget) -> Boolean): InspectionTarget = runBlocking {
    withTimeout(10_000) {
      (state.first { it is InspectorState.Inspecting && predicate(it.target) } as InspectorState.Inspecting).target
    }
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
}
