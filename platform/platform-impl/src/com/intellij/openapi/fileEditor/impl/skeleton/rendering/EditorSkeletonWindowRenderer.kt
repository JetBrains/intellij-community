// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.skeleton.EditorSkeleton
import com.intellij.ui.paint.use
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.awt.geom.Area
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * Renders the skeleton off the EDT into the graphics of its window. The renderer does not turn on AWT mixing.
 *
 * [renderThread] renders a frame while the renderer is showing and its window is not minimized, each time the colors change.
 * A frame does not draw over a component that Swing paints on top of the renderer.
 * While such a component is large, or the glass pane paints over the renderer, a frame goes through a Swing repaint.
 * The EDT paints the current frame each time Swing repaints the renderer.
 */
internal class EditorSkeletonWindowRenderer(
  private val renderThread: EditorSkeletonRenderThread = service(),
) : JComponent(), EditorSkeletonRenderer {
  override val component: JComponent
    get() = this

  @Volatile
  private var target: EditorSkeletonWindowTarget? = null

  @Volatile
  private var stopped = false

  @Volatile
  private var failed = false

  private var frames: EditorSkeletonFrames? = null
  private val targetUpdater = TargetUpdater()
  private var trackedContainers: Set<Container> = emptySet()
  private var trackedComponents: Set<Component> = emptySet()
  private var trackedWindow: Window? = null

  private val backBuffer = EditorSkeletonBackBuffer()
  private var renderedTarget: EditorSkeletonWindowTarget? = null
  private var renderedColors: EditorSkeletonColorManager.FrameColors? = null
  private var lastChangeNanos = 0L
  private var settled = true

  init {
    isOpaque = true
    isFocusable = false
    addHierarchyListener(targetUpdater)
    addHierarchyBoundsListener(targetUpdater)
    addPropertyChangeListener("graphicsConfiguration", targetUpdater)
  }

  override fun startRendering(cs: CoroutineScope, frames: EditorSkeletonFrames) {
    if (this.frames != null) {
      return
    }
    this.frames = frames
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        stop()
      }
    }
    updateTarget()
  }

  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  override fun reshape(x: Int, y: Int, w: Int, h: Int) {
    super.reshape(x, y, w, h)
    if (frames != null) {
      updateTarget()
    }
  }

  override fun removeNotify() {
    track(containers = emptySet(), components = emptySet(), window = null)
    publish(null)
    super.removeNotify()
  }

  override fun paintComponent(g: Graphics) {
    val frames = frames
    if (frames == null) {
      g.color = EditorSkeleton.EDITOR_BACKGROUND_COLOR
      g.fillRect(0, 0, width, height)
      return
    }
    if (isTargetStale()) {
      updateTarget()
    }
    frames.paint(g as Graphics2D, width, height, JBUIScale.scale(1f), frames.currentColors())
  }

  /**
   * Renders one frame when its colors or its target differ from the last rendered frame. Only [EditorSkeletonRenderThread] calls it.
   */
  internal fun renderFrame() {
    if (stopped || failed) {
      renderThread.unregister(this)
      return
    }
    val target = target ?: return
    val colors = target.frames.currentColors()
    val now = System.nanoTime()
    if (colors == renderedColors && target === renderedTarget) {
      if (!settled && now - lastChangeNanos > SETTLE_NANOS) {
        settled = true
        repaint()
      }
      return
    }
    lastChangeNanos = now
    if (target.covered) {
      repaint()
      rendered(target, colors)
      settled = true
      return
    }
    val buffer = backBuffer.createOrUpdateImageBuffer(target.graphicsConfiguration, target.width, target.height)
    buffer.createGraphics().use { g ->
      target.frames.paint(g, target.width, target.height, target.scale, colors)
    }
    if (buffer.contentsLost()) {
      return
    }
    val windowGraphics = target.window.graphics as? Graphics2D ?: return
    val drawn = windowGraphics.use { g ->
      g.clipRect(target.clip.x, target.clip.y, target.clip.width, target.clip.height)
      if (target.excluded.isNotEmpty()) {
        g.clip(clipWithoutOverlays(target))
      }
      g.drawImage(buffer, target.origin.x, target.origin.y, null)
    }
    if (!drawn) {
      return
    }
    rendered(target, colors)
    settled = false
    if (this.target !== target) {
      target.rootPane.repaint(target.rootPaneClip)
    }
  }

  /**
   * Releases the back buffer and forgets the last rendered frame. Only [EditorSkeletonRenderThread] calls it, between frames.
   */
  internal fun releaseBackBuffer() {
    backBuffer.release()
    rendered(target = null, colors = null)
    settled = true
  }

  /**
   * Stops the frames off the EDT for good, and asks Swing for the current frame.
   * [EditorSkeletonRenderThread] calls it after a frame fails. It returns `true` only for the first failure.
   */
  internal fun renderFailed(): Boolean {
    val first = !failed
    failed = true
    renderThread.unregister(this)
    repaint()
    return first
  }

  private fun rendered(target: EditorSkeletonWindowTarget?, colors: EditorSkeletonColorManager.FrameColors?) {
    renderedTarget = target
    renderedColors = colors
  }

  private fun stop() {
    stopped = true
    publish(null)
  }

  private fun publish(newTarget: EditorSkeletonWindowTarget?) {
    val oldTarget = target
    if (newTarget != oldTarget) {
      target = newTarget
      if (oldTarget != null) {
        repairAfterChange(oldTarget, newTarget)
      }
    }
    if (newTarget != null && !stopped && !failed) {
      renderThread.register(this)
    } else {
      renderThread.unregister(this)
    }
  }

  /**
   * Asks Swing to repaint the areas where a frame with [oldTarget] can have drawn over a component that is now on top of the renderer.
   * A frame can be late by one posted bounds event.
   */
  private fun repairAfterChange(oldTarget: EditorSkeletonWindowTarget, newTarget: EditorSkeletonWindowTarget?) {
    if (newTarget == null || newTarget.window !== oldTarget.window || newTarget.clip != oldTarget.clip) {
      oldTarget.rootPane.repaint(oldTarget.rootPaneClip)
      return
    }
    val dx = newTarget.rootPaneClip.x - newTarget.clip.x
    val dy = newTarget.rootPaneClip.y - newTarget.clip.y
    for (overlay in newTarget.excluded) {
      if (overlay !in oldTarget.excluded) {
        newTarget.rootPane.repaint(overlay.x + dx, overlay.y + dy, overlay.width, overlay.height)
      }
    }
  }

  private fun isTargetStale(): Boolean {
    val target = target ?: return isShowing
    val origin = SwingUtilities.convertPoint(this, 0, 0, target.window)
    return origin != target.origin || width != target.width || height != target.height
  }

  private fun updateTarget() {
    val rootPane = if (isShowing) rootPane else null
    val window = rootPane?.let { SwingUtilities.getWindowAncestor(it) }
    if (rootPane == null || window == null) {
      track(containers = emptySet(), components = emptySet(), window = null)
      publish(null)
      return
    }
    val containers = identitySet<Container>()
    val components = identitySet<Component>()
    collectOverlayHolders(rootPane, containers, components)
    track(containers, components, window)
    publish(computeTarget(rootPane, window))
  }

  private fun computeTarget(rootPane: JRootPane, window: Window): EditorSkeletonWindowTarget? {
    val frames = frames
    if (frames == null || stopped || failed || width <= 0 || height <= 0) {
      return null
    }
    if (window is Frame && (window.extendedState and Frame.ICONIFIED) != 0) {
      return null
    }
    val graphicsConfiguration = graphicsConfiguration ?: return null
    val visibleRect = visibleRect
    if (visibleRect.isEmpty) {
      return null
    }
    val clip = SwingUtilities.convertRectangle(this, visibleRect, window)
    val overlays = findOverlays(rootPane, window, clip)
    return EditorSkeletonWindowTarget(
      window = window,
      graphicsConfiguration = graphicsConfiguration,
      origin = SwingUtilities.convertPoint(this, 0, 0, window),
      width = width,
      height = height,
      clip = clip,
      excluded = overlays.excluded,
      rootPane = rootPane,
      rootPaneClip = SwingUtilities.convertRectangle(this, visibleRect, rootPane),
      scale = JBUIScale.scale(1f),
      covered = overlays.covered,
      frames = frames,
    )
  }

  /**
   * Finds the components that Swing paints on top of the renderer within [clip]: the siblings before each ancestor of the renderer,
   * and the children of the glass pane.
   */
  private fun findOverlays(rootPane: JRootPane, window: Window, clip: Rectangle): Overlays {
    val excluded = ArrayList<Rectangle>()
    var covered = false
    forEachComponentOnTop(rootPane) { parent, component ->
      if (!component.isVisible) {
        return@forEachComponentOnTop
      }
      if (component === rootPane.glassPane) {
        if (paintsOverChildren(component)) {
          covered = true
          return@forEachComponentOnTop
        }
        for (child in (component as? Container)?.components.orEmpty()) {
          if (child.isVisible) {
            addOverlay(SwingUtilities.convertRectangle(component, child.bounds, window), clip, excluded)
          }
        }
        return@forEachComponentOnTop
      }
      addOverlay(SwingUtilities.convertRectangle(parent, component.bounds, window), clip, excluded)
    }
    val excludedArea = excluded.sumOf { it.width.toLong() * it.height.toLong() }
    if (excludedArea * LARGE_OVERLAY_DIVISOR > clip.width.toLong() * clip.height.toLong()) {
      covered = true
    }
    return Overlays(excluded = if (covered) emptyList() else excluded, covered = covered)
  }

  private fun addOverlay(bounds: Rectangle, clip: Rectangle, excluded: MutableList<Rectangle>) {
    val overlap = bounds.intersection(clip)
    if (!overlap.isEmpty) {
      excluded.add(overlap)
    }
  }

  private fun paintsOverChildren(glassPane: Component): Boolean {
    return glassPane !is JComponent || glassPane.isOpaque || !glassPane.isOptimizedDrawingEnabled
  }

  private fun collectOverlayHolders(rootPane: JRootPane, containers: MutableSet<Container>, components: MutableSet<Component>) {
    var ancestor: Container? = parent
    while (ancestor != null && ancestor !is Window) {
      containers.add(ancestor)
      ancestor = ancestor.parent
    }
    forEachComponentOnTop(rootPane) { _, component ->
      components.add(component)
      if (component === rootPane.glassPane && component is Container) {
        containers.add(component)
        components.addAll(component.components)
      }
    }
  }

  /**
   * Calls [action] for each component that Swing paints after the branch of the renderer.
   * It goes from the parent of the renderer up to [rootPane].
   * Swing paints the child at index 0 of a container last, so the children before the branch are on top of it.
   */
  private inline fun forEachComponentOnTop(rootPane: JRootPane, action: (parent: Container, component: Component) -> Unit) {
    var branch: Component = this
    var parent: Container? = branch.parent
    while (parent != null && parent !is Window) {
      val index = parent.getComponentZOrder(branch)
      for (i in 0 until index) {
        action(parent, parent.getComponent(i))
      }
      if (parent === rootPane) {
        return
      }
      branch = parent
      parent = parent.parent
    }
  }

  private fun clipWithoutOverlays(target: EditorSkeletonWindowTarget): Area {
    val area = Area(target.clip)
    for (overlay in target.excluded) {
      area.subtract(Area(overlay))
    }
    return area
  }

  /**
   * Listens to [containers], [components] and [window], and stops listening to the components of the previous call.
   * It runs inside hierarchy events, so it must not add or remove a hierarchy listener: AWT counts them while it dispatches.
   */
  private fun track(containers: Set<Container>, components: Set<Component>, window: Window?) {
    for (container in trackedContainers) {
      if (container !in containers) {
        container.removeContainerListener(targetUpdater)
      }
    }
    for (container in containers) {
      if (container !in trackedContainers) {
        container.addContainerListener(targetUpdater)
      }
    }
    for (component in trackedComponents) {
      if (component !in components) {
        component.removeComponentListener(targetUpdater)
        component.removePropertyChangeListener(OPAQUE_PROPERTY, targetUpdater)
      }
    }
    for (component in components) {
      if (component !in trackedComponents) {
        component.addComponentListener(targetUpdater)
        component.addPropertyChangeListener(OPAQUE_PROPERTY, targetUpdater)
      }
    }
    trackedContainers = containers
    trackedComponents = components
    if (window !== trackedWindow) {
      trackedWindow?.removeWindowStateListener(targetUpdater)
      window?.addWindowStateListener(targetUpdater)
      trackedWindow = window
    }
  }

  private class Overlays(val excluded: List<Rectangle>, val covered: Boolean)

  private inner class TargetUpdater :
    ComponentListener,
    HierarchyListener,
    HierarchyBoundsListener,
    ContainerListener,
    WindowStateListener,
    PropertyChangeListener {
    override fun componentResized(e: ComponentEvent) {
      updateTarget()
    }

    override fun componentMoved(e: ComponentEvent) {
      updateTarget()
    }

    override fun componentShown(e: ComponentEvent) {
      updateTarget()
    }

    override fun componentHidden(e: ComponentEvent) {
      updateTarget()
    }

    override fun hierarchyChanged(e: HierarchyEvent) {
      updateTarget()
    }

    override fun ancestorMoved(e: HierarchyEvent) {
      updateTarget()
    }

    override fun ancestorResized(e: HierarchyEvent) {
      updateTarget()
    }

    override fun componentAdded(e: ContainerEvent) {
      updateTarget()
    }

    override fun componentRemoved(e: ContainerEvent) {
      updateTarget()
    }

    override fun windowStateChanged(e: WindowEvent) {
      updateTarget()
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
      updateTarget()
    }
  }
}

private fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())

private const val OPAQUE_PROPERTY = "opaque"

/**
 * The overlays may hide at most one quarter of the renderer. Above that, the frames go through Swing repaints.
 */
private const val LARGE_OVERLAY_DIVISOR = 4L

/**
 * The time after the last color change when the render thread asks Swing to paint the current frame once.
 * Then the Swing back buffer holds the current frame.
 */
private const val SETTLE_NANOS = 250_000_000L
