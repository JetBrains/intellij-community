// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.project.Project
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.intellij.IconTestRun
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.impl.rendering.debug.ComposePaintInfo
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Point
import java.lang.reflect.Proxy
import javax.swing.JPanel

/** Picking and inspecting icons drawn by Compose (captures created via `IconRenderDebugger.traceComposePaint`). */
@TestApplication
class ComposeInspectionTest {
  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
  }

  @Test
  fun `compose hosts are recognized and the index finds the innermost icon under a point`() {
    testIcons {
      val (host, panel) = runInEdtAndGet { composeHost() }
      assertTrue(ComposeIcons.isComposeHost(host))
      assertTrue(ComposeIcons.isComposeHost(panel))
      assertFalse(ComposeIcons.isComposeHost(JPanel()))
      assertSame(host, ComposeIcons.hostOf(panel))
      assertSame(panel, ComposeIcons.panelOfHost(host))

      val big = createTestRun(iconDescriptor { image(testImage(32, 32)) })
      val small = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val index = ComposeIconIndex()
      index.add(composeCapture(big, host, info(x = 0f, y = 0f, size = 32f)))
      index.add(composeCapture(small, host, info(x = 8f, y = 8f, size = 16f)))

      assertSame(small.renderer, index.find(host, 10f, 10f)!!.iconRenderer)
      assertSame(big.renderer, index.find(host, 2f, 2f)!!.iconRenderer)
      assertNull(index.find(host, 40f, 40f))
      assertNull(index.find(JPanel(), 10f, 10f))

      // a later draw of the same renderer replaces its entry (the icon moved)
      index.add(composeCapture(small, host, info(x = 100f, y = 100f, size = 16f)))
      assertSame(big.renderer, index.find(host, 10f, 10f)!!.iconRenderer)
      index.clear(host)
      assertNull(index.find(host, 2f, 2f))
    }
  }

  @Test
  fun `picked compose icon is inspected from its captured draw`() {
    testIcons {
      val (host, panel) = runInEdtAndGet { composeHost() }
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val other = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val index = ComposeIconIndex()
      index.add(composeCapture(run, host, info(x = 20f, y = 10f, size = 16f)))

      val picked = runInEdtAndGet { ComponentIconExtractor.findComposeIcon(panel, scenePoint(panel, 25f, 15f), index) }
      assertNotNull(picked)
      picked!!
      assertSame(host, picked.component)
      assertNull(picked.icon)
      assertEquals("Compose icon in JPanel", picked.description)
      assertTrue(picked.label.startsWith("Compose · "), picked.label)
      assertNotNull(picked.highlightBounds)

      val scope = CoroutineScope(SupervisorJob())
      val controller = IconInspectorControllerImpl(unusedProject(), scope)
      try {
        runInEdtAndWait { controller.inspect(picked, newGeneration = true) }
        // a draw elsewhere in the same host is not the picked icon
        runInEdtAndWait { composePaint(other, host, info(x = 60f, y = 10f, size = 16f)) }
        runInEdtAndWait { composePaint(run, host, info(x = 20f, y = 10f, size = 16f)) }
        val target = controller.awaitTarget { it.stability?.paints == 1 }

        assertEquals("Compose icon in JPanel", target.componentDescription)
        assertFalse(target.bridged)
        val root = target.root
        assertEquals(InspectorNodeOrigin.ICONS_API, root.origin)
        assertTrue(root.properties.any { it.group == ComposeIcons.GROUP_COMPOSE && it.name == "density (Compose)" })
        assertTrue(root.properties.any { it.group == ComposeIcons.GROUP_COMPOSE && it.name == "bounds (scene px)" })
        val iconNode = root.children.single()
        assertTrue(iconNode.debuggable)
        assertEquals(InspectorNodeOrigin.ICONS_API, iconNode.origin)
        assertTrue(target.rendering.properties.any { it.group == ComposeIcons.GROUP_COMPOSE })

        // the captured renderer can be flagged and is hit by the next Compose draw
        val layerNode = iconNode.children.first { it.debuggable }
        runInEdtAndWait { controller.setFlagged(layerNode.id, true) }
        runInEdtAndWait { composePaint(run, host, info(x = 20f, y = 10f, size = 16f)) }
        val flaggedRenderer = IconRenderDebugger.flaggedRenderers().single()
        assertEquals(1, IconRenderDebugger.hitCount(flaggedRenderer))
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
  fun `compose element not rendered by the icons API gets a semantics-only target`() {
    testIcons {
      val (host, panel) = runInEdtAndGet { composeHost() }
      val pick = ComposePick(host, panel, 5f, 5f, capture = null,
                             semantics = ComposeSemanticsImage("Settings", "Image", null, 0f, 0f, 16f, 16f))
      val picked = PickedIcon(host, host, null, "Compose icon in JPanel", emptyList(), null, Point(2, 2), pick)
      assertTrue(picked.label.contains("not icons API"), picked.label)
      val scope = CoroutineScope(SupervisorJob())
      val controller = IconInspectorControllerImpl(unusedProject(), scope)
      try {
        runInEdtAndWait { controller.inspect(picked, newGeneration = true) }
        val target = (controller.state.value as InspectorState.Inspecting).target
        assertEquals(InspectorNodeOrigin.LEGACY, target.root.origin)
        assertNotNull(target.root.warning)
        assertTrue(target.root.properties.any { it.name == "content description" && it.value == "Settings" })
        assertFalse(target.root.debuggable)
        assertTrue(target.root.children.isEmpty())
      }
      finally {
        runInEdtAndWait { controller.dispose() }
        scope.cancel()
      }
    }
  }

  private fun composeHost(): Pair<JPanel, ComposePanel> {
    val panel = ComposePanel()
    val host = JPanel(BorderLayout())
    host.add(panel, BorderLayout.CENTER)
    host.setSize(200, 100)
    host.doLayout()
    return host to panel
  }

  /** A point in [panel] coordinates for the given scene pixel. */
  private fun scenePoint(panel: ComposePanel, x: Float, y: Float): Point {
    val scale = ComposeIcons.sceneScale(panel)
    return Point((x / scale).toInt(), (y / scale).toInt())
  }

  private fun info(x: Float, y: Float, size: Float) =
    ComposePaintInfo(JPanel::class.java.name, x, y, size, size, density = 1f, scale = null, finalWidth = size.toInt(),
                     finalHeight = size.toInt())

  private fun composePaint(run: IconTestRun, host: Any, info: ComposePaintInfo) {
    IconRenderDebugger.traceComposePaint(run.renderer, run.renderer.iconDescriptor, host, { info }) { run.performRender() }
  }

  private fun composeCapture(run: IconTestRun, host: Any, info: ComposePaintInfo): RenderCapture {
    val captures = mutableListOf<RenderCapture>()
    IconRenderDebugger.capture({ it === host }) { captures.add(it) }.use { composePaint(run, host, info) }
    return captures.single()
  }

  private fun unusedProject(): Project =
    Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, _, _ -> null } as Project

  private fun IconInspectorControllerImpl.awaitTarget(predicate: (InspectionTarget) -> Boolean): InspectionTarget = runBlocking {
    withTimeout(10_000) {
      (state.first { it is InspectorState.Inspecting && predicate(it.target) } as InspectorState.Inspecting).target
    }
  }
}
