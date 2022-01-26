// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.StripeButtonManager
import com.intellij.toolWindow.createDragImage
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max

internal class LayoutData(
  @JvmField
  var eachX: Int = 0,
  @JvmField
  var eachY: Int = 0,
  @JvmField
  var size: Dimension = JBUI.emptySize(),
  @JvmField
  var fitSize: Dimension = JBUI.emptySize(),
  @JvmField
  var horizontal: Boolean = false,
  @JvmField
  var dragTargetChosen: Boolean = false,
  @JvmField
  var dragToSide: Boolean = false,
  @JvmField
  var shouldSwapCoordinates: Boolean = false,
  @JvmField
  var dragInsertPosition: Int = 0,
)

internal abstract class AbstractDroppableStripe(layoutManager: LayoutManager) : JPanel(layoutManager) {
  companion object {
    const val DROP_DISTANCE_SENSITIVITY = 200

    @VisibleForTesting
    fun createButtonLayoutComparator(isNewStripes: Boolean, anchor: ToolWindowAnchor): Comparator<StripeButtonManager> {
      return Comparator { o1, o2 ->
        // side buttons in the end
        if (o1.windowDescriptor.isSplit != o2.windowDescriptor.isSplit) {
          if (o1.windowDescriptor.isSplit) 1 else -1
        }
        else if (isNewStripes && anchor == ToolWindowAnchor.BOTTOM) {
          // left bottom for new ui has a reverse order because user checks buttons in this location from bottom to top,
          // also because new buttons without a predefined order should not change the existing button location
          getOrderForComparator(o2) - getOrderForComparator(o1)
        }
        else {
          getOrderForComparator(o1) - getOrderForComparator(o2)
        }
      }
    }

    private fun getOrderForComparator(manager: StripeButtonManager): Int {
      val order = manager.windowDescriptor.order
      return if (order == -1) Int.MAX_VALUE else order
    }
  }

  private var dragButton: JComponent? = null
  protected var dropRectangle: Rectangle = Rectangle(-1, -1)
  protected val drawRectangle = Rectangle()
  private var dragButtonImage: JComponent? = null
  protected var isFinishingDrop = false
  private var lastLayoutData: LayoutData? = null

  private val buttons: MutableList<StripeButtonManager> = mutableListOf()

  abstract val isNewStripes: Boolean
  abstract val anchor: ToolWindowAnchor

  private val stripeButtonManagerComparator by lazy(LazyThreadSafetyMode.NONE) { createButtonLayoutComparator(isNewStripes, anchor) }

  fun getButtons(): List<StripeButtonManager> = buttons

  fun addButton(button: StripeButtonManager) {
    computedPreferredSize = null
    buttons.add(button)
    add(button.getComponent())
  }

  fun removeButton(button: StripeButtonManager) {
    computedPreferredSize = null
    buttons.remove(button)
    remove(button.getComponent())
    revalidate()
  }

  @JvmField
  protected var computedPreferredSize: Dimension? = null

  open fun reset() {
    lastLayoutData = null
    computedPreferredSize = null
  }

  fun resetDrop() {
    (dragButton as? SquareStripeButton)?.resetDrop()

    dragButton = null
    dragButtonImage = null
    isFinishingDrop = false
    computedPreferredSize = null
    revalidate()
    repaint()
  }

  fun processDropButton(button: JComponent, buttonImage: JComponent, screenPoint: Point) {
    if (!isDroppingButton()) {
      button.createDragImage()?.let { buttonImage.paint(it.graphics) }

      dragButton = button
      dragButtonImage = buttonImage
      computedPreferredSize = null
    }

    val dropPoint = screenPoint.location.also {
      SwingUtilities.convertPointFromScreen(it, this)
      it.y = max(it.y, 0)
    }
    dropRectangle = if (isNewStripes) Rectangle(dropPoint, button.preferredSize) else Rectangle(dropPoint, buttonImage.size)

    revalidate()
    repaint()
  }

  open fun stickDropPoint(point: Point) {
  }

  override fun getPreferredSize(): Dimension? {
    if (computedPreferredSize == null) {
      computedPreferredSize = recomputeBounds(setBounds = false, toFitWith = null, noDrop = false).size
    }
    return computedPreferredSize
  }

  override fun invalidate() {
    computedPreferredSize = null
    super.invalidate()
  }

  fun isDroppingButton() = dragButton != null

  abstract fun getButtonFor(toolWindowId: String): StripeButtonManager?

  fun finishDrop(manager: ToolWindowManagerImpl) {
    val lastLayoutData = lastLayoutData ?: return
    if (!isDroppingButton()) {
      return
    }

    isFinishingDrop = true
    dragButton?.let(::getToolWindowFor)?.let {
      var order = lastLayoutData.dragInsertPosition
      if (isNewStripes && anchor == ToolWindowAnchor.BOTTOM) {
        order++
      }
      manager.setSideToolAndAnchor(it.id, anchor, order, !isNewStripes && lastLayoutData.dragToSide)
    }
    manager.invokeLater { resetDrop() }
  }

  fun getDropToSide(): Boolean? {
    return if (lastLayoutData == null || !lastLayoutData!!.dragTargetChosen) null else lastLayoutData!!.dragToSide
  }

  override fun doLayout() {
    if (!isFinishingDrop) {
      lastLayoutData = recomputeBounds(setBounds = true, toFitWith = size, noDrop = false)
    }
  }

  open fun isHorizontal() : Boolean = false
  open fun containsPoint(screenPoint: Point): Boolean = Rectangle(locationOnScreen, size).contains(screenPoint)

  protected abstract fun getToolWindowFor(component: JComponent): ToolWindowImpl?

  protected fun recomputeBounds(setBounds: Boolean, toFitWith: Dimension?, noDrop: Boolean): LayoutData {
    val horizontalOffset = height
    val data = LayoutData(horizontal = isHorizontal(), dragInsertPosition = -1)
    if (data.horizontal) {
      data.eachX = horizontalOffset - 1
      data.eachY = 1
    }

    val dragButton = dragButton
    val processDrop = dragButton != null && !noDrop &&
                      containsPoint(dropRectangle.location.also { SwingUtilities.convertPointToScreen(it, this) })
    if (!isNewStripes && dragButton != null) {
      data.shouldSwapCoordinates = anchor.isHorizontal != getToolWindowFor(dragButton)?.anchor?.isHorizontal
    }
    data.fitSize = toFitWith ?: JBUI.emptySize()

    if (toFitWith == null) {
      getButtons().asSequence().map { it.getComponent() }.filter { it.isVisible }.map { it.preferredSize }.forEach {
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
          if (data.shouldSwapCoordinates) dropRectangle.height else dropRectangle.width
        }
        else {
          if (data.shouldSwapCoordinates) dropRectangle.width else dropRectangle.height
        }
      }
      gap = max(gap, 0)
    }

    var sidesStarted = false
    for (b in getButtonsToLayOut()) {
      val button = b.getComponent()
      val eachSize = button.preferredSize
      val windowInfo = b.windowDescriptor

      val insertOrder = windowInfo.order
      val isSplit = windowInfo.isSplit

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
          val distance = dropRectangle.x - data.eachX
          val delta = dropRectangle.x + if (data.shouldSwapCoordinates) dropRectangle.height else dropRectangle.width
          distance < eachSize.width / 2 || delta < eachSize.width / 2
        }
        else {
          val distance = dropRectangle.y - data.eachY
          val delta = dropRectangle.y + if (data.shouldSwapCoordinates) dropRectangle.width else dropRectangle.height
          distance < eachSize.height / 2 || delta < eachSize.height / 2
        }

        if (update) {
          data.dragInsertPosition = insertOrder
          data.dragToSide = sidesStarted
          layoutDragButton(data, 0)
          data.dragTargetChosen = true
        }
      }
      layoutButton(data, button, setBounds, shouldSwapCoordinates = false)
    }

    if (!sidesStarted && processDrop && !data.dragTargetChosen) {
      tryDroppingOnGap(data, gap, -1)
    }

    dragButton?.let {
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
      layoutDragButton(data, 0)
      data.dragTargetChosen = true
    }

    if (!data.dragTargetChosen) {
      drawRectangle.apply { x = 0; y = 0; width = 0; height = 0}
    }
    return data
  }

  private fun swap(d: Dimension) {
    val tmp = d.width
    d.width = d.height
    d.height = tmp
  }

  private fun layoutButton(data: LayoutData, button: JComponent, setBounds: Boolean, shouldSwapCoordinates: Boolean) {
    val preferredSize = button.preferredSize
    if (shouldSwapCoordinates) {
      swap(preferredSize)
    }

    if (setBounds) {
      val width = if (data.horizontal) preferredSize.width else data.fitSize.width
      val height = if (data.horizontal) data.fitSize.height else preferredSize.height
      button.setBounds(data.eachX, data.eachY, width, height)
    }

    if (data.horizontal) {
      val deltaX = preferredSize.width
      data.eachX += deltaX
      data.size.width += deltaX
      data.size.height = max(data.size.height, preferredSize.height)
    }
    else {
      val deltaY = preferredSize.height
      data.eachY += deltaY
      data.size.width = max(data.size.width, preferredSize.width)
      data.size.height += deltaY
    }
  }

  protected open fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
    val nonSideDistance = max(0, if (data.horizontal) dropRectangle.x - data.eachX else dropRectangle.y - data.eachY)
    val sideDistance = if (data.horizontal) data.eachX + gap - dropRectangle.x else data.eachY + gap - dropRectangle.y

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

  protected fun layoutDragButton(data: LayoutData, gap: Int = 0) {
    drawRectangle.x = data.eachX
    drawRectangle.y = data.eachY
    if (isNewStripes) {
      dragButton?.let { layoutButton(data, it, setBounds = false, shouldSwapCoordinates = false) }
    }
    else {
      dragButtonImage?.let { layoutButton(data, it, setBounds = false, shouldSwapCoordinates = data.shouldSwapCoordinates) }
    }

    if (data.horizontal) {
      drawRectangle.width = data.eachX - drawRectangle.x
      drawRectangle.height = data.fitSize.height
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          drawRectangle.x = width - height - drawRectangle.width
        }
        else {
          drawRectangle.x += gap
        }
      }
    }
    else {
      drawRectangle.width = data.fitSize.width
      drawRectangle.height = data.eachY - drawRectangle.y
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          drawRectangle.y = height - drawRectangle.height
        }
        else {
          drawRectangle.y += gap
        }
      }
    }
  }

  private fun getButtonsToLayOut(): List<StripeButtonManager> {
    if (buttons.isEmpty()) {
      return emptyList()
    }

    val tools = ArrayList<StripeButtonManager>(buttons.size)
    buttons.filterTo(tools) { it.getComponent().isVisible }
    tools.sortWith(stripeButtonManagerComparator)
    return tools
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (!isFinishingDrop && isDroppingButton()) {
      g.color = if (isNewStripes) JBUI.CurrentTheme.ToolWindow.DragAndDrop.STRIPE_BACKGROUND else background.brighter()
      g.fillRect(0, 0, width, height)
      val rectangle = drawRectangle
      if (!rectangle.isEmpty) {
        g.color = if (isNewStripes) JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_DROP_BACKGROUND else JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
        RectanglePainter.FILL.paint(g as Graphics2D, rectangle.x, rectangle.y, rectangle.width, rectangle.height, null)
      }
    }
  }

  fun updatePresentation() {
    buttons.forEach(StripeButtonManager::updatePresentation)
  }
}