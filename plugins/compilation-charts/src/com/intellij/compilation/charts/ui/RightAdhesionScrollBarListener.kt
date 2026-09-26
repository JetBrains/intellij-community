// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Point
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.util.concurrent.ScheduledFuture
import javax.swing.JViewport

internal class RightAdhesionScrollBarListener(
  private val viewport: JViewport,
  private val zoom: Zoom,
  private val shouldScroll: AutoScrollingType
) : AdjustmentListener, MouseWheelListener {
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("Compilation charts adjust value listener", 1)
  private var lastZoomEvent = ZoomEvent.RESET
  private var updateShouldScrollTask: ScheduledFuture<*>? = null
  private var lastNewPosition: Point? = null
  override fun adjustmentValueChanged(e: AdjustmentEvent) {
    val point = lastNewPosition
    lastNewPosition = null
    if (point != null && !shouldScroll.isActive()) {
      viewport.viewPosition = point
    }
    if (e.valueIsAdjusting) {
      updateShouldScroll()
    }
    adjustHorizontalScrollToRightIfNeeded()
  }

  override fun mouseWheelMoved(e: MouseWheelEvent) {
    if (e.isControlDown) {
      shouldScroll.stop()
      scheduleUpdateShouldScroll()
    } else {
      updateShouldScroll(e.unitsToScroll)
    }
  }

  private fun scheduleUpdateShouldScroll() {
    updateShouldScrollTask?.cancel(false)
    updateShouldScrollTask = executor.schedule(::updateShouldScroll, Settings.Scroll.timeout, Settings.Scroll.unit)
  }

  private fun adjustHorizontalScrollToRightIfNeeded() {
    if (shouldScroll.isActive()) {
      viewport.viewPosition = Point(viewport.viewSize.width - viewport.width, viewport.viewPosition.y)
    }
  }

  private fun updateShouldScroll(additionalValue: Int = 0) {
    if (!shouldScroll.isEnabled()) return
    if (viewport.viewPosition.x + viewport.width + additionalValue >= viewport.viewSize.width)
      shouldScroll.start()
    else
      shouldScroll.stop()
  }

  internal fun scrollToEnd() {
    shouldScroll.start()
    adjustHorizontalScrollToRightIfNeeded()
  }

  private fun disableShouldScroll() {
    shouldScroll.stop()
  }

  fun increase() = applyZoomTransformation(ZoomEvent.IN) { adjust(viewport, viewport.getMiddlePoint(), Settings.Zoom.IN) }

  fun decrease() = applyZoomTransformation(ZoomEvent.OUT) { adjust(viewport, viewport.getMiddlePoint(), Settings.Zoom.OUT) }

  fun reset() = applyZoomTransformation(ZoomEvent.RESET) {
    val shouldScrollAfterResetting = viewport.width >= viewport.viewSize.width
    lastNewPosition = reset(viewport, viewport.getMiddlePoint())
    if (shouldScrollAfterResetting) scrollToEnd()
  }

  private fun applyZoomTransformation(event: ZoomEvent, transformation: Zoom.() -> Unit) {
    if (lastZoomEvent == ZoomEvent.RESET && event == ZoomEvent.RESET) return
    lastZoomEvent = event
    disableShouldScroll()
    zoom.transformation()
    scheduleUpdateShouldScroll()
  }

  private fun JViewport.getMiddlePoint(): Int = viewPosition.x + width / 2
}

