// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.icons.impl.rendering.debug.ComposePaintInfo
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import java.util.IdentityHashMap
import java.util.WeakHashMap
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.floor

private val LOG = logger<ComposeIcons>()

/** A Compose element with image semantics (e.g. a Jewel `Icon(painter)`), found via the semantics tree. */
internal class ComposeSemanticsImage(
  val contentDescription: String?,
  val role: String?,
  val testTag: String?,
  /** Bounds in pixels of the Compose scene. */
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

/** What was picked inside a Compose host. */
internal class ComposePick(
  /** The paint target Compose icons report (Jewel's `LocalComponent`, e.g. `JewelComposePanelWrapper`), or the panel. */
  val host: Component,
  val composePanel: ComposePanel,
  /** Picked point, in pixels of the Compose scene. */
  val scenePointX: Float,
  val scenePointY: Float,
  /** Last captured paint of the icons-API icon under the point; `null` if none was seen (legacy painter icon). */
  val capture: RenderCapture?,
  /** Image semantics under the point, if any (also the only source of information for legacy painter icons). */
  val semantics: ComposeSemanticsImage?,
) {
  val hostDescription: String get() = simpleClassName(host)
}

/**
 * Compose support: hosts, coordinate conversion between the Compose scene (pixels) and AWT, and hit-testing via the
 * semantics tree. Compose icons rendered by the icons API are found through render captures (their draws report their
 * bounds, see `IconRenderDebugger.traceComposePaint`), which needs no Compose internals.
 */
internal object ComposeIcons {
  /** The [ComposePanel] [component] belongs to (itself or an ancestor), or `null`. */
  fun composePanelOf(component: Component): ComposePanel? =
    component as? ComposePanel ?: SwingUtilities.getAncestorOfClass(ComposePanel::class.java, component) as? ComposePanel

  /**
   * The paint target Compose icons of [panel] report: Jewel provides its wrapper panel (`JewelComposePanelWrapper`, the
   * parent holding only [panel]) as `LocalComponent`; otherwise the panel itself.
   */
  fun hostOf(panel: ComposePanel): Component {
    val parent = panel.parent
    return if (parent != null && isComposeHost(parent)) parent else panel
  }

  /** Cheap check usable on the rendering path: is [component] a Compose host (a [ComposePanel] or a wrapper of exactly one)? */
  fun isComposeHost(component: Any?): Boolean {
    if (component is ComposePanel) return true
    if (component !is Container) return false
    return component.componentCount == 1 && component.getComponent(0) is ComposePanel
  }

  /** The [ComposePanel] of a Compose [host] (see [hostOf]). */
  fun panelOfHost(host: Any?): ComposePanel? = when {
    host is ComposePanel -> host
    host is Container && host.componentCount == 1 -> host.getComponent(0) as? ComposePanel
    else -> null
  }

  /** Pixels of the Compose scene per AWT unit (the system scale of the panel's screen). */
  fun sceneScale(panel: Component): Float {
    val transformScale = panel.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat()
    return if (transformScale != null && transformScale > 0f) transformScale else JBUIScale.sysScale(panel)
  }

  /** Converts [point] (in [panel] coordinates) to pixels of the Compose scene. */
  fun toScene(panel: ComposePanel, point: Point): Pair<Float, Float> {
    val scale = sceneScale(panel)
    return (point.x * scale) to (point.y * scale)
  }

  /** Scene-pixel bounds as an AWT rectangle in [panel] coordinates (rounded outwards). */
  fun sceneToPanel(panel: ComposePanel, left: Float, top: Float, right: Float, bottom: Float): Rectangle {
    val scale = sceneScale(panel)
    val x = floor(left / scale).toInt()
    val y = floor(top / scale).toInt()
    return Rectangle(x, y, maxOf(1, ceil(right / scale).toInt() - x), maxOf(1, ceil(bottom / scale).toInt() - y))
  }

  fun sceneToPanel(panel: ComposePanel, info: ComposePaintInfo): Rectangle =
    sceneToPanel(panel, info.x, info.y, info.x + info.width, info.y + info.height)

  /**
   * The innermost node with image semantics (role Image or a content description) under the scene point, or `null`.
   * Uses the semantics tree published for tooling/accessibility (`ComposePanel.semanticsOwners`, `@ComposeToolingApi`).
   * Note: Jewel's `Icon` only adds semantics when it has a content description.
   */
  @OptIn(ComposeToolingApi::class)
  fun imageAt(panel: ComposePanel, x: Float, y: Float): ComposeSemanticsImage? {
    val nodes = try {
      panel.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    }
    catch (e: Exception) {
      // the semantics tree can be transiently inconsistent while it is mutated
      LOG.debug(e)
      return null
    }
    var best: SemanticsNode? = null
    var bestArea = Float.MAX_VALUE
    for (node in nodes) {
      val config = node.config
      val isImage = config.getOrNull(SemanticsProperties.Role) == Role.Image || config.contains(SemanticsProperties.ContentDescription)
      if (!isImage) continue
      val bounds = node.boundsInWindow
      if (x < bounds.left || y < bounds.top || x >= bounds.right || y >= bounds.bottom) continue
      val area = bounds.width * bounds.height
      if (area < bestArea) {
        best = node
        bestArea = area
      }
    }
    val node = best ?: return null
    val config = node.config
    val bounds = node.boundsInWindow
    return ComposeSemanticsImage(
      contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", "),
      role = config.getOrNull(SemanticsProperties.Role)?.toString(),
      testTag = config.getOrNull(SemanticsProperties.TestTag),
      left = bounds.left,
      top = bounds.top,
      right = bounds.right,
      bottom = bounds.bottom,
    )
  }

  /** Properties of a Compose pick / capture, in the "Compose" group. */
  fun properties(pick: ComposePick?, capture: RenderCapture?): List<InspectorProperty> {
    val rows = ArrayList<InspectorProperty>()
    val info = capture?.composePaintInfo
    val host = pick?.host ?: capture?.paintTarget
    val hostText = if (host == null) info?.hostClass ?: "unknown (no LocalComponent)" else className(host)
    rows.add(InspectorProperty("host", hostText, GROUP_COMPOSE))
    if (info != null) {
      val bounds = "(${number(info.x)}, ${number(info.y)}) ${number(info.width)}×${number(info.height)}"
      rows.add(InspectorProperty("bounds (scene px)", bounds, GROUP_COMPOSE))
      val panel = pick?.composePanel ?: panelOfHost(host)
      if (panel != null) {
        val awt = sceneToPanel(panel, info)
        rows.add(InspectorProperty("bounds (AWT, in ComposePanel)", "(${awt.x}, ${awt.y}) ${awt.width}×${awt.height}", GROUP_COMPOSE))
        rows.add(InspectorProperty("scene scale (system)", number(sceneScale(panel)), GROUP_COMPOSE))
      }
      rows.add(InspectorProperty("density (Compose)", number(info.density), GROUP_COMPOSE))
      rows.add(InspectorProperty("icon scale", info.scale ?: "none", GROUP_COMPOSE))
      rows.add(InspectorProperty("final size (px)", InspectorFormatting.size(info.finalWidth, info.finalHeight), GROUP_COMPOSE))
    }
    val semantics = pick?.semantics
    if (semantics != null) {
      rows.add(InspectorProperty("content description", semantics.contentDescription ?: "none", GROUP_COMPOSE))
      rows.add(InspectorProperty("role", semantics.role ?: "none", GROUP_COMPOSE))
      semantics.testTag?.let { rows.add(InspectorProperty("test tag", it, GROUP_COMPOSE)) }
      rows.add(InspectorProperty("semantics bounds (scene px)",
                                 "(${number(semantics.left)}, ${number(semantics.top)}) " +
                                 "${number(semantics.right - semantics.left)}×${number(semantics.bottom - semantics.top)}", GROUP_COMPOSE))
    }
    return rows
  }

  const val GROUP_COMPOSE: String = "Compose"
}

/**
 * Latest captured Compose icon draws per host, for the picker's hover hit-test: icons-API icons drawn by Compose report
 * their bounds through render captures. One entry per renderer instance (one per `Icon` composable). Thread-safe.
 */
internal class ComposeIconIndex {
  private val byHost = WeakHashMap<Any, IdentityHashMap<Any, RenderCapture>>()

  fun add(capture: RenderCapture) {
    val host = capture.paintTarget ?: return
    if (capture.composePaintInfo == null) return
    synchronized(byHost) {
      byHost.getOrPut(host) { IdentityHashMap() }[capture.iconRenderer] = capture
    }
  }

  fun clear(host: Any) {
    synchronized(byHost) { byHost.remove(host) }
  }

  fun clearAll() {
    synchronized(byHost) { byHost.clear() }
  }

  /** The innermost (smallest), then most recently drawn icon of [host] whose bounds contain the scene point. */
  fun find(host: Any, x: Float, y: Float): RenderCapture? {
    val candidates = synchronized(byHost) { byHost[host]?.values?.toList() } ?: return null
    return candidates
      .filter { it.composePaintInfo!!.contains(x, y) }
      .minWithOrNull(compareBy<RenderCapture> { it.composePaintInfo!!.width * it.composePaintInfo!!.height }
                       .thenByDescending { it.timestampNanos })
  }
}
