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
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * Renders the skeleton off the EDT into the graphics of its window. It is a lightweight component, so it does not turn on AWT mixing.
 *
 * [renderThread] renders the frames while the renderer is showing and its window is not minimized.
 * While an overlay can be on top of the renderer, a frame goes through a Swing repaint, so the overlay stays on top.
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
  private var swingRepaintsOnly = false

  private var paintFrame: ((Graphics2D, Int, Int, Float) -> Unit)? = null
  private val targetUpdater = TargetUpdater()
  private var trackedRootPane: JRootPane? = null
  private var trackedWindow: Window? = null
  private val backBuffer = EditorSkeletonBackBuffer()

  init {
    isOpaque = true
    isFocusable = false
    addComponentListener(targetUpdater)
    addHierarchyListener(targetUpdater)
    addHierarchyBoundsListener(targetUpdater)
    addPropertyChangeListener("graphicsConfiguration", targetUpdater)
  }

  override fun startRendering(cs: CoroutineScope, paintFrame: (Graphics2D, Int, Int, Float) -> Unit) {
    if (this.paintFrame != null) {
      return
    }
    this.paintFrame = paintFrame
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

  override fun removeNotify() {
    track(rootPane = null, window = null)
    publish(null)
    super.removeNotify()
  }

  override fun paintComponent(g: Graphics) {
    val paintFrame = paintFrame
    if (paintFrame == null) {
      g.color = EditorSkeleton.EDITOR_BACKGROUND_COLOR
      g.fillRect(0, 0, width, height)
      return
    }
    paintFrame(g as Graphics2D, width, height, JBUIScale.scale(1f))
  }

  /**
   * Renders one frame. Only [EditorSkeletonRenderThread] calls it.
   */
  internal fun renderFrame() {
    val target = target
    if (target == null || stopped) {
      return
    }
    if (target.covered || swingRepaintsOnly) {
      repaint()
      return
    }
    val buffer = backBuffer.createOrUpdateImageBuffer(target.graphicsConfiguration, target.width, target.height)
    buffer.createGraphics().use { g ->
      target.paintFrame(g, target.width, target.height, target.scale)
    }
    if (buffer.contentsLost()) {
      return
    }
    val windowGraphics = target.window.graphics ?: return
    windowGraphics.use { g ->
      g.clipRect(target.clip.x, target.clip.y, target.clip.width, target.clip.height)
      g.drawImage(buffer, target.origin.x, target.origin.y, null)
    }
    if (this.target !== target) {
      target.rootPane.repaint(target.rootPaneClip)
    }
  }

  /**
   * Releases the back buffer. Only [EditorSkeletonRenderThread] calls it, between frames.
   */
  internal fun releaseBackBuffer() {
    backBuffer.release()
  }

  /**
   * Makes each later frame go through a Swing repaint. [EditorSkeletonRenderThread] calls it after a frame fails.
   */
  internal fun useSwingRepaints() {
    swingRepaintsOnly = true
  }

  private fun stop() {
    stopped = true
    publish(null)
  }

  private fun publish(newTarget: EditorSkeletonWindowTarget?) {
    target = newTarget
    if (newTarget != null && !stopped) {
      renderThread.register(this)
    } else {
      renderThread.unregister(this)
    }
  }

  private fun updateTarget() {
    val rootPane = if (isShowing) rootPane else null
    track(rootPane, rootPane?.let { SwingUtilities.getWindowAncestor(it) })
    publish(computeTarget(rootPane))
  }

  private fun computeTarget(rootPane: JRootPane?): EditorSkeletonWindowTarget? {
    val paintFrame = paintFrame
    if (paintFrame == null || stopped || rootPane == null || width <= 0 || height <= 0) {
      return null
    }
    val window = SwingUtilities.getWindowAncestor(this) ?: return null
    if (window is Frame && (window.extendedState and Frame.ICONIFIED) != 0) {
      return null
    }
    val graphicsConfiguration = graphicsConfiguration ?: return null
    val visibleRect = visibleRect
    if (visibleRect.isEmpty) {
      return null
    }
    return EditorSkeletonWindowTarget(
      window = window,
      graphicsConfiguration = graphicsConfiguration,
      origin = SwingUtilities.convertPoint(this, 0, 0, window),
      width = width,
      height = height,
      clip = SwingUtilities.convertRectangle(this, visibleRect, window),
      rootPane = rootPane,
      rootPaneClip = SwingUtilities.convertRectangle(this, visibleRect, rootPane),
      scale = JBUIScale.scale(1f),
      covered = isCovered(rootPane, SwingUtilities.convertRectangle(this, visibleRect, rootPane.layeredPane)),
      paintFrame = paintFrame,
    )
  }

  private fun isCovered(rootPane: JRootPane, area: Rectangle): Boolean {
    if (rootPane.glassPane.isVisible) {
      return true
    }
    val layeredPane = rootPane.layeredPane
    for (component in layeredPane.components) {
      val isOverlay = layeredPane.getLayer(component) > JLayeredPane.FRAME_CONTENT_LAYER
      if (isOverlay && component.isVisible && component.bounds.intersects(area)) {
        return true
      }
    }
    return false
  }

  private fun track(rootPane: JRootPane?, window: Window?) {
    if (rootPane !== trackedRootPane) {
      trackedRootPane?.let { untrackRootPane(it) }
      rootPane?.let { trackRootPane(it) }
      trackedRootPane = rootPane
    }
    if (window !== trackedWindow) {
      trackedWindow?.removeWindowStateListener(targetUpdater)
      window?.addWindowStateListener(targetUpdater)
      trackedWindow = window
    }
  }

  private fun trackRootPane(rootPane: JRootPane) {
    rootPane.layeredPane.addContainerListener(targetUpdater)
    for (component in rootPane.layeredPane.components) {
      component.addComponentListener(targetUpdater)
    }
    rootPane.glassPane.addComponentListener(targetUpdater)
  }

  private fun untrackRootPane(rootPane: JRootPane) {
    rootPane.layeredPane.removeContainerListener(targetUpdater)
    for (component in rootPane.layeredPane.components) {
      component.removeComponentListener(targetUpdater)
    }
    rootPane.glassPane.removeComponentListener(targetUpdater)
  }

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
      e.child.addComponentListener(this)
      updateTarget()
    }

    override fun componentRemoved(e: ContainerEvent) {
      e.child.removeComponentListener(this)
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
