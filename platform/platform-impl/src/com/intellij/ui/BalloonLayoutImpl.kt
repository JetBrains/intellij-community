// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.notification.Notification
import com.intellij.notification.impl.NotificationCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.Runnable
import java.util.function.IntSupplier
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import kotlin.time.Duration.Companion.milliseconds

private val visibleCount: Int
  get() = Registry.intValue("ide.notification.visible.count", 2)

@OptIn(FlowPreview::class)
internal open class BalloonLayoutImpl(private val parent: JRootPane, insets: Insets) : BalloonLayout {
  private val resizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      queueRelayout()
    }
  }

  @JvmField
  protected var layeredPane: JLayeredPane? = null
  private val insets: Insets

  @JvmField
  protected val balloons = ArrayList<Balloon>()
  protected val layoutData = HashMap<Balloon, BalloonLayoutData>()
  private var widthSupplier: IntSupplier? = null
  private val relayoutRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @JvmField
  protected val layoutRunnable = Runnable {
    calculateSize()
    relayout()
    fireRelayout()
  }

  private val listeners = ArrayList<Runnable>()

  private val coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope.childScope()

  init {
    layeredPane = parent.layeredPane
    this.insets = insets
    layeredPane!!.addComponentListener(resizeListener)

    coroutineScope.launch {
      relayoutRequests
        .debounce(200.milliseconds)
        .collect {
          withContext(Dispatchers.EDT) {
            if (layeredPane != null) {
              relayout()
              fireRelayout()
            }
          }
        }
    }
  }

  override fun closeAll() {
    for (balloon in ArrayList(balloons)) {
      val layoutData = layoutData.get(balloon)
      if (layoutData != null) {
        NotificationCollector.getInstance()
          .logNotificationBalloonClosedByUser(layoutData.project, layoutData.id, layoutData.displayId, layoutData.groupId)
      }
      remove(balloon, true)
    }
  }

  open fun dispose() {
    coroutineScope.cancel()

    val layeredPane = layeredPane ?: return
    layeredPane.removeComponentListener(resizeListener)
    for (balloon in ArrayList(balloons)) {
      Disposer.dispose(balloon)
    }
    balloons.clear()
    layoutData.clear()
    listeners.clear()
    this.layeredPane = null
  }

  fun addListener(listener: Runnable) {
    listeners.add(listener)
  }

  fun removeListener(listener: Runnable) {
    listeners.remove(listener)
  }

  protected fun fireRelayout() {
    for (listener in listeners) {
      listener.run()
    }
  }

  val topBalloonComponent: Component?
    get() = (balloons.lastOrNull() as? BalloonImpl)?.component

  override fun add(balloon: Balloon) {
    add(newBalloon = balloon, layoutData = null)
  }

  @RequiresEdt
  override fun add(newBalloon: Balloon, layoutData: Any?) {
    val merge = merge(layoutData)
    if (merge == null) {
      if (!balloons.isEmpty() && balloons.size == visibleCount) {
        remove(balloons.get(0))
      }
      balloons.add(newBalloon)
    }
    else {
      val index = balloons.indexOf(merge)
      remove(merge)
      balloons.add(index, newBalloon)
    }
    if (layoutData is BalloonLayoutData) {
      layoutData.closeAll = Runnable(::closeAll)
      layoutData.doLayout = layoutRunnable
      this.layoutData.put(newBalloon, layoutData)
    }
    Disposer.register(newBalloon) {
      remove(balloon = newBalloon, hide = false)
      queueRelayout()
    }
    calculateSize()
    relayout()
    if (!newBalloon.isDisposed) {
      newBalloon.show(layeredPane)
    }
    fireRelayout()
  }

  private fun merge(data: Any?): Balloon? {
    val mergeId: String? = when (data) {
      is String -> data
      is BalloonLayoutData -> data.groupId
      else -> null
    }

    if (mergeId != null) {
      for ((key, value) in layoutData) {
        if (mergeId == value.groupId) {
          return key
        }
      }
    }
    return null
  }

  open fun preMerge(notification: Notification): BalloonLayoutData.MergeInfo? {
    return layoutData.get(merge(notification.groupId) ?: return null)?.merge()
  }

  fun remove(notification: Notification) {
    val balloon = merge(notification.groupId)
    if (balloon != null) {
      remove(balloon = balloon, hide = true)
    }
  }

  protected fun remove(balloon: Balloon) {
    remove(balloon = balloon, hide = false)
    balloon.hide(true)
    fireRelayout()
  }

  protected open fun remove(balloon: Balloon, hide: Boolean) {
    balloons.remove(balloon)
    layoutData.remove(balloon)?.mergeData = null
    if (hide) {
      balloon.hide(true)
      fireRelayout()
    }
  }

  fun closeFirst() {
    if (!balloons.isEmpty()) {
      remove(balloon = balloons.get(0), hide = true)
    }
  }

  val balloonCount: Int
    get() = balloons.size

  protected open fun getSize(balloon: Balloon): Dimension {
    val layoutData = layoutData.get(balloon)
    if (layoutData == null) {
      val size = balloon.preferredSize
      val widthSupplier = widthSupplier
      return if (widthSupplier == null) size else Dimension(widthSupplier.asInt, size.height)
    }
    return Dimension(widthSupplier!!.asInt, layoutData.height)
  }

  val isEmpty: Boolean
    get() = balloons.isEmpty()

  open fun queueRelayout() {
    check(relayoutRequests.tryEmit(Unit))
  }

  protected open fun calculateSize() {
    widthSupplier = null
    for (balloon in balloons) {
      val layoutData = layoutData[balloon]
      if (layoutData != null) {
        layoutData.height = balloon.preferredSize.height
      }
    }
    widthSupplier = IntSupplier { BalloonLayoutConfiguration.FixedWidth() }
  }

  protected fun relayout() {
    if (layeredPane == null) return
    val size = layeredPane!!.size
    JBInsets.removeFrom(size, insets)
    val layoutRec = Rectangle(Point(insets.left, insets.top), size)
    var columns = createColumns(layoutRec)
    while (columns.size > 1) {
      remove(balloons[0], true)
      columns = createColumns(layoutRec)
    }
    val pane = UIUtil.findComponentOfType(parent, ToolWindowPane::class.java)
    val layeredPane = pane?.getLayeredPane()
    var eachColumnX = (if (layeredPane == null) this.layeredPane!!.width else layeredPane.x + layeredPane.width) - 4
    if (pane != null && ExperimentalUI.isNewUI()) {
      eachColumnX += pane.x
    }
    doLayout(balloons = columns[0], startX = eachColumnX + 4, bottomY = this.layeredPane!!.bounds.maxY.toInt())
  }

  private fun doLayout(balloons: List<Balloon>, startX: Int, bottomY: Int) {
    var y = bottomY
    val pane = UIUtil.findComponentOfType(parent, ToolWindowPane::class.java)
    val helper = getFrameHelper(parent.parent as? Window)
    if (pane != null) {
      y -= pane.bottomHeight
      if (SystemInfoRt.isMac && !ExperimentalUI.isNewUI()) {
        if (helper == null || !helper.isInFullScreen) {
          y -= UIUtil.getTransparentTitleBarHeight(parent)
        }
      }
    }
    if (helper != null) {
      val statusBar = helper.statusBar?.component
      if (statusBar != null && statusBar.isVisible) {
        y -= statusBar.height
      }
    }
    setBounds(balloons = balloons, startX = startX, startY = y)
  }

  protected open fun setBounds(balloons: List<Balloon>, startX: Int, startY: Int) {
    var y = startY
    for (balloon in balloons) {
      val bounds = Rectangle(getSize(balloon))
      y -= bounds.height
      bounds.setLocation(startX - bounds.width, y)
      balloon.setBounds(bounds)
    }
  }

  private fun createColumns(layoutRec: Rectangle): List<ArrayList<Balloon>> {
    val columns = ArrayList<ArrayList<Balloon>>()
    var eachColumn = ArrayList<Balloon>()
    columns.add(eachColumn)
    var eachColumnHeight = 0
    for (each in balloons) {
      val eachSize = getSize(each)
      if (eachColumnHeight + eachSize.height > layoutRec.getHeight()) {
        eachColumn = ArrayList()
        columns.add(eachColumn)
        eachColumnHeight = 0
      }
      eachColumn.add(each)
      eachColumnHeight += eachSize.height
    }
    return columns
  }
}
