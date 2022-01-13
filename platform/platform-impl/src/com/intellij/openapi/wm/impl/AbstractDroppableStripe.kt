// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max

class LayoutData(
  var eachX: Int = 0,
  var eachY: Int = 0,
  var size: Dimension = JBUI.emptySize(),
  var fitSize: Dimension = JBUI.emptySize(),
  var horizontal: Boolean = false,
  var dragTargetChosen: Boolean = false,
  var dragToSide: Boolean = false,
  var shouldSwapCoordinates: Boolean = false,
  var dragInsertPosition: Int = 0)

fun JComponent.getAnchor() : ToolWindowAnchor? = when (this) {
  is StripeButton -> anchor
  is SquareStripeButton -> button.anchor
  else -> null
}

abstract class AbstractDroppableStripe(layoutManager: LayoutManager) : JPanel(layoutManager) {

  private var myDragButton: JComponent? = null
  protected var myDropRectangle: Rectangle = Rectangle(-1, -1)
  protected val myDrawRectangle = Rectangle()
  private var myDragButtonImage: JComponent? = null
  protected var myFinishingDrop = false
  private var myLastLayoutData: LayoutData? = null
  protected var myPreferredSize: Dimension? = null

  val buttons: ArrayList<JComponent> = ArrayList()

  open fun reset() {
    myLastLayoutData = null
    myPreferredSize = null
  }

  fun resetDrop() {
    (myDragButton as? SquareStripeButton)?.resetDrop()

    myDragButton = null
    myDragButtonImage = null
    myFinishingDrop = false
    myPreferredSize = null
    revalidate()
    repaint()
  }

  fun processDropButton(button: JComponent, buttonImage: JComponent, screenPoint: Point) {
    if (!isDroppingButton()) {
      button.createDragImage()?.let { buttonImage.paint(it.graphics) }

      myDragButton = button
      myDragButtonImage = buttonImage
      myPreferredSize = null
    }

    val dropPoint = screenPoint.location.also {
      SwingUtilities.convertPointFromScreen(it, this)
      it.y = max(it.y, 0)
    }
    myDropRectangle = if (ExperimentalUI.isNewToolWindowsStripes()) Rectangle(dropPoint, button.preferredSize) else Rectangle(dropPoint, buttonImage.size)

    revalidate()
    repaint()
  }

  open fun stickDropPoint(point: Point) {
  }

  override fun getPreferredSize(): Dimension? {
    if (myPreferredSize == null) {
      myPreferredSize = if (!ExperimentalUI.isNewToolWindowsStripes() && buttons.isEmpty()) JBUI.emptySize() else recomputeBounds(false, null, false).size
    }
    return myPreferredSize
  }

  override fun invalidate() {
    myPreferredSize = null
    super.invalidate()
  }

  fun isDroppingButton() = myDragButton != null

  abstract fun getAnchor() : ToolWindowAnchor
  abstract fun getButtonFor(toolWindowId: String): JComponent?

  private fun getToolwindowID() : String? = myDragButton?.let {
    when (it) {
      is StripeButton -> it.toolWindow.id
      is SquareStripeButton -> it.button.toolWindow.id
      else -> null
    }
  }

  fun finishDrop(manager: ToolWindowManagerImpl) {
    if (myLastLayoutData == null || !isDroppingButton()) return

    myFinishingDrop = true
    getToolwindowID()?.let {
      if (ExperimentalUI.isNewToolWindowsStripes()) manager.setLargeStripeAnchor(it, getAnchor(), myLastLayoutData!!.dragInsertPosition, true)
      else manager.setSideToolAndAnchor(it, getAnchor(), myLastLayoutData!!.dragInsertPosition, myLastLayoutData!!.dragToSide)
    }
    manager.invokeLater { resetDrop() }
  }

  fun getDropToSide(): Boolean? {
    return if (myLastLayoutData == null || !myLastLayoutData!!.dragTargetChosen) null else myLastLayoutData!!.dragToSide
  }

  override fun doLayout() {
    if (!myFinishingDrop) {
      myLastLayoutData = recomputeBounds(true, size, false)
    }
  }

  open fun isHorizontal() : Boolean = false
  open fun containsPoint(screenPoint: Point): Boolean = Rectangle(locationOnScreen, size).contains(screenPoint)

  protected fun recomputeBounds(setBounds: Boolean, toFitWith: Dimension?, noDrop: Boolean): LayoutData {
    val horizontalOffset = height
    val data = LayoutData(horizontal = isHorizontal(), dragInsertPosition = -1).apply {
      if (horizontal) {
        eachX = horizontalOffset - 1
        eachY = 1
      }
    }

    data.shouldSwapCoordinates = !ExperimentalUI.isNewToolWindowsStripes() && getAnchor().isHorizontal != myDragButton?.getAnchor()?.isHorizontal
    data.fitSize = toFitWith ?: JBUI.emptySize()

    val dropScreenPoint = myDropRectangle.location.also { SwingUtilities.convertPointToScreen(it, this) }
    val processDrop = isDroppingButton() && containsPoint(dropScreenPoint) && !noDrop

    if (toFitWith == null) {
      buttons.filter { it.isVisible }.map { it.preferredSize }.forEach {
        data.fitSize.width = max(it.width, data.fitSize.width)
        data.fitSize.height = max(it.height, data.fitSize.height)
      }
    }
    var gap = 0
    if (toFitWith != null) {
      val layoutData = recomputeBounds(false, null, true)

      gap = if (data.horizontal) {
        toFitWith.width - horizontalOffset - layoutData.size.width - data.eachX
      }
      else {
        toFitWith.height - layoutData.size.height - data.eachY
      }

      if (processDrop) {
        gap -= if (data.horizontal) {
          if (data.shouldSwapCoordinates) myDropRectangle.height else myDropRectangle.width
        }
        else {
          if (data.shouldSwapCoordinates) myDropRectangle.width else myDropRectangle.height
        }
      }
      gap = max(gap, 0)
    }

    //var insertOrder: Int
    var sidesStarted = false
    for (button in getButtonsToLayOut()) {
      val eachSize = button.preferredSize
      val windowInfo = when (button) {
        is StripeButton -> button.windowInfo
        is SquareStripeButton -> button.button.windowInfo
        else -> null
      }

      val insertOrder = windowInfo?.let { if (ExperimentalUI.isNewToolWindowsStripes()) it.orderOnLargeStripe else it.order } ?: 0
      val isSplit = windowInfo?.isSplit ?: false

      if (!sidesStarted && isSplit) {
        if (processDrop && !data.dragTargetChosen) {
          tryDroppingOnGap(data, gap, insertOrder)
        }
        if (data.horizontal) {
          data.eachX += gap
          data.size.width += gap
        }
        else {
          data.eachY += gap
          data.size.height += gap
        }
        sidesStarted = true
      }

      if (processDrop && !data.dragTargetChosen) {
        val update = if (data.horizontal) {
          val distance = myDropRectangle.x - data.eachX
          val delta = myDropRectangle.x + if (data.shouldSwapCoordinates) myDropRectangle.height else myDropRectangle.width
          distance < eachSize.width / 2 || delta < eachSize.width / 2
        }
        else {
          val distance = myDropRectangle.y - data.eachY
          val delta = myDropRectangle.y + if (data.shouldSwapCoordinates) myDropRectangle.width else myDropRectangle.height
          distance < eachSize.height / 2 || delta < eachSize.height / 2
        }

        if (update) {
          data.dragInsertPosition = insertOrder
          data.dragToSide = sidesStarted
          layoutDragButton(data)
          data.dragTargetChosen = true
        }
      }
      layoutButton(data, button, setBounds)
    }

    if (!sidesStarted && processDrop && !data.dragTargetChosen) {
      tryDroppingOnGap(data, gap, -1)
    }

    myDragButton?.let {
      val dragSize = it.preferredSize
      if (data.shouldSwapCoordinates) {
        swap(dragSize)
      }
      data.size.width = max(data.size.width, dragSize.width)
      data.size.height = max(data.size.height, dragSize.height)
    }

    if (processDrop && !data.dragTargetChosen) {
      data.dragInsertPosition = -1
      data.dragToSide = true
      layoutDragButton(data)
      data.dragTargetChosen = true
    }

    if (!data.dragTargetChosen) myDrawRectangle.apply { x = 0; y = 0; width = 0; height = 0}
    return data
  }

  private fun swap(d: Dimension) {
    val tmp = d.width
    d.width = d.height
    d.height = tmp
  }

  private fun layoutButton(data: LayoutData, button: JComponent, setBounds: Boolean) {
    button.preferredSize.let {
      if (data.shouldSwapCoordinates && button !is StripeButton) swap(it)

      if (setBounds) {
        val width = if (data.horizontal) it.width else data.fitSize.width
        val height = if (data.horizontal) data.fitSize.height else it.height
        button.setBounds(data.eachX, data.eachY, width, height)
      }

      if (data.horizontal) {
        val deltaX = it.width
        data.eachX += deltaX
        data.size.width += deltaX
        data.size.height = max(data.size.height, it.height)
      }
      else {
        val deltaY = it.height
        data.eachY += deltaY
        data.size.width = max(data.size.width, it.width)
        data.size.height += deltaY
      }
    }
  }

  protected open fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
    val nonSideDistance = max(0, if (data.horizontal) myDropRectangle.x - data.eachX else myDropRectangle.y - data.eachY)
    val sideDistance = if (data.horizontal) data.eachX + gap - myDropRectangle.x else data.eachY + gap - myDropRectangle.y

    if (sideDistance > 0) {
      if (nonSideDistance > sideDistance) {
        data.dragInsertPosition = insertOrder
        data.dragToSide = true
      }
      else {
        data.dragInsertPosition = -1
        data.dragToSide = false
      }
      data.dragTargetChosen = true
      layoutDragButton(data, gap)
    }
  }

  private fun layoutDragButton(data: LayoutData) {
    layoutDragButton(data, 0)
  }

  protected fun layoutDragButton(data: LayoutData, gap: Int) {
    myDrawRectangle.x = data.eachX
    myDrawRectangle.y = data.eachY
    if (ExperimentalUI.isNewToolWindowsStripes()) {
      myDragButton?.let{ layoutButton(data, it, false) }
    } else {
      myDragButtonImage?.let { layoutButton(data, it, false) }
    }

    if (data.horizontal) {
      myDrawRectangle.width = data.eachX - myDrawRectangle.x
      myDrawRectangle.height = data.fitSize.height
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          myDrawRectangle.x = width - height - myDrawRectangle.width
        }
        else {
          myDrawRectangle.x += gap
        }
      }
    }
    else {
      myDrawRectangle.width = data.fitSize.width
      myDrawRectangle.height = data.eachY - myDrawRectangle.y
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          myDrawRectangle.y = height - myDrawRectangle.height
        }
        else {
          myDrawRectangle.y += gap
        }
      }
    }
  }

  private fun getButtonsToLayOut(): List<JComponent> {
    val tools: MutableList<JComponent> = mutableListOf()
    val sideTools: MutableList<JComponent> = mutableListOf()
    //for (button in buttons()) {
    //  if (!button.isVisible) {
    //    continue
    //  }
    //
    //  if (button is StripeButton) {
    //    if (button.windowInfo.isSplit) {
    //      sideTools.add(button)
    //    }
    //    else {
    //      tools.add(button)
    //    }
    //  }
    //}
    //result.addAll(tools)
    //result.addAll(sideTools)

    buttons.filter { it.isVisible }.forEach {
      if (it is StripeButton) {
        if (it.windowInfo.isSplit) sideTools.add(it) else tools.add(it)
      }
      else if (it is SquareStripeButton) {
        if (it.button.windowInfo.isSplit) sideTools.add(it) else tools.add(it)
      }
    }

    return tools + sideTools
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (!myFinishingDrop && isDroppingButton()) {
      val expUI = ExperimentalUI.isNewToolWindowsStripes()
      g.color = if (expUI) JBUI.CurrentTheme.ToolWindow.DragAndDrop.STRIPE_BACKGROUND else background.brighter()
      g.fillRect(0, 0, width, height)
      val rectangle = myDrawRectangle
      if (!rectangle.isEmpty) {
        g.color = if (expUI) JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_DROP_BACKGROUND else JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
        RectanglePainter.FILL.paint(g as Graphics2D, rectangle.x, rectangle.y, rectangle.width, rectangle.height, null)
      }
    }
  }
}