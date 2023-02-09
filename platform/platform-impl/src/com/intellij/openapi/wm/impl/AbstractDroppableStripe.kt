// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.StripeButtonManager
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.components.JBPanel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import javax.swing.Icon
import javax.swing.JComponent
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
  var isSplit: Boolean = false,
  @JvmField
  var shouldSwapCoordinates: Boolean = false,
  @JvmField
  var dragInsertPosition: Int = 0,
)

internal abstract class AbstractDroppableStripe(val paneId: String, layoutManager: LayoutManager)
  : JBPanel<AbstractDroppableStripe>(layoutManager) {
  companion object {
    const val DROP_DISTANCE_SENSITIVITY = 200

    @VisibleForTesting
    fun createButtonLayoutComparator(isNewUi: Boolean, anchor: ToolWindowAnchor): Comparator<StripeButtonManager> {
      return Comparator { o1, o2 ->
        // side buttons in the end
        if (o1.windowDescriptor.isSplit != o2.windowDescriptor.isSplit) {
          if (o1.windowDescriptor.isSplit) 1 else -1
        }
        else if (isNewUi && anchor == ToolWindowAnchor.BOTTOM) {
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

  init {
    putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, false)
  }

  private var dragButton: StripeButtonManager? = null
  protected var dropRectangle: Rectangle = Rectangle(-1, -1)
  protected val drawRectangle = Rectangle()
  private var dragButtonImage: JComponent? = null
  private var isFinishingDrop = false
  private var lastLayoutData: LayoutData? = null

  private val buttons: MutableList<StripeButtonManager> = mutableListOf()

  abstract val isNewStripes: Boolean
  abstract val anchor: ToolWindowAnchor
  open val split: Boolean = false

  private val stripeButtonManagerComparator by lazy(LazyThreadSafetyMode.NONE) { createButtonLayoutComparator(isNewStripes, anchor) }

  private var separatorTopSide = false
  private val separator = StripeButtonSeparator().also {
    it.isVisible = false
  }
  private var separatorStripe = object : StripeButtonManager {
    override val id = ""
    override val toolWindow: ToolWindowImpl
      get() = throw UnsupportedOperationException()
    override val windowDescriptor = WindowInfoImpl()

    override fun updateState(toolWindow: ToolWindowImpl) {
    }

    override fun updatePresentation() {
    }

    override fun updateIcon(icon: Icon?) {
    }

    override fun remove(anchor: ToolWindowAnchor, split: Boolean) {
    }

    override fun getComponent() = separator
  }

  fun getButtons(): List<StripeButtonManager> = buttons

  fun addButton(button: StripeButtonManager) {
    computedPreferredSize = null
    buttons.add(button)
    add(button.getComponent())
    revalidate()
  }

  fun removeButton(button: StripeButtonManager) {
    computedPreferredSize = null
    buttons.remove(button)
    remove(button.getComponent())
    revalidate()
    repaint()
  }

  @JvmField
  protected var computedPreferredSize: Dimension? = null

  fun reset() {
    lastLayoutData = null
    computedPreferredSize = null
    buttons.clear()
    removeAll()
    revalidate()
  }

  fun resetDrop() {
    (dragButton?.getComponent() as? SquareStripeButton)?.resetDrop()

    dragButton = null
    dragButtonImage = null
    isFinishingDrop = false
    computedPreferredSize = null
    revalidate()
    repaint()
  }

  fun processDropButton(button: StripeButtonManager, buttonImage: JComponent, devicePoint: DevicePoint) {
    if (!isDroppingButton()) {
      dragButton = button
      dragButtonImage = buttonImage
      computedPreferredSize = null
    }

    val dropPoint = devicePoint.getLocationOnScreen(this).location.also {
      SwingUtilities.convertPointFromScreen(it, this)
      // If the major axis of the drop point is not within the bounds of a stripe, the drop highlight isn't drawn. This isn't a problem for
      // the old UI (x is always valid for full width horizontal stripe, etc.), but it is for the new UI's TOP and BOTTOM stripes, which are
      // treated as vertical, and are not full height. Coerce the drop point to be within the stripe's bounds' major axis. Take into account
      // hidden and empty stripes. (And remember that height is the number of pixels, so 0 <= y < h)
      if (isNewStripes) {
        it.y = if (height > 0) it.y.coerceIn(0, height - 1) else max(it.y, 0)
      }
    }
    dropRectangle = if (isNewStripes) Rectangle(dropPoint, button.getComponent().preferredSize) else Rectangle(dropPoint, buttonImage.size)

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
    dragButton?.toolWindow?.let {
      var order = lastLayoutData.dragInsertPosition
      if (isNewStripes && anchor == ToolWindowAnchor.BOTTOM) {
        order++
      }
      val isSplit = if (isNewStripes) lastLayoutData.isSplit else lastLayoutData.dragToSide
      manager.setSideToolAndAnchor(it.id, paneId, anchor, order, isSplit)
    }
    manager.invokeLater { resetDrop() }
  }

  open fun getDropToSide(): Boolean? {
    return if (lastLayoutData == null || !lastLayoutData!!.dragTargetChosen) null else lastLayoutData!!.dragToSide || lastLayoutData!!.isSplit
  }

  override fun doLayout() {
    doLayout(size)
  }

  protected open fun doLayout(size: Dimension) {
    if (!isFinishingDrop) {
      lastLayoutData = recomputeBounds(setBounds = true, toFitWith = size, noDrop = false)
    }
  }

  open fun isHorizontal() : Boolean = false
  open fun containsPoint(screenPoint: Point): Boolean {
    return isShowing && Rectangle(locationOnScreen, size).contains(screenPoint)
  }

  open fun getToolWindowDropAreaScreenBounds() = Rectangle(locationOnScreen, size)

  protected fun recomputeBounds(setBounds: Boolean, toFitWith: Dimension?, noDrop: Boolean): LayoutData {
    val horizontalOffset = height
    val data = LayoutData(horizontal = isHorizontal(), dragInsertPosition = -1)
    if (data.horizontal) {
      data.eachX = horizontalOffset - 1
      data.eachY = 1
    }

    val dragButton = dragButton
    val p = dropRectangle.location.also { SwingUtilities.convertPointToScreen(it, this) }
    val processDrop = dragButton != null && !noDrop
    if (!isNewStripes && dragButton != null) {
      data.shouldSwapCoordinates = anchor.isHorizontal != dragButton.toolWindow.anchor.isHorizontal
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
    val buttonsToLayOut = getButtonsToLayOut(processDrop)
    var addSize = separatorTopSide

    if (separatorTopSide) {
      if (data.horizontal) {
        data.eachX += dropRectangle.width
      }
      else {
        data.eachY += dropRectangle.height
      }
    }

    for (b in buttonsToLayOut) {
      val button = b.getComponent()
      val eachSize = button.preferredSize
      val windowInfo = b.windowDescriptor

      val insertOrder = windowInfo.order
      val isSplit = windowInfo.isSplit

      if (!sidesStarted && isSplit && !isNewStripes) {
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
          if (separatorTopSide && buttonsToLayOut.indexOf(b) == 0) {
            if (data.horizontal) {
              data.eachX -= dropRectangle.width
            }
            else {
              data.eachY -= dropRectangle.height
            }
            addSize = false
          }
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

    dragButton?.getComponent()?.let {
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

    if (processDrop && separator.isVisible && separator.dndState) {
      if (!separatorTopSide) {
        val separatorLocation = if (data.horizontal) separator.x else separator.y
        val dropLocation = if (data.horizontal) drawRectangle.x + drawRectangle.width else drawRectangle.y + drawRectangle.height
        addSize = dropLocation <= separatorLocation
      }
      if (addSize) {
        if (data.horizontal) {
          data.size.width += drawRectangle.width
        }
        else {
          data.size.height += drawRectangle.height
        }
      }
    }

    data.isSplit = split
    if (!split && isNewStripes && separator.isVisible) {
      if (data.horizontal) {
        data.isSplit = drawRectangle.x > separator.x
      }
      else {
        data.isSplit = drawRectangle.y > separator.y
      }
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
      dragButton?.getComponent()?.let { layoutButton(data, it, setBounds = false, shouldSwapCoordinates = false) }
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

  private fun getButtonsToLayOut(processDrop: Boolean): List<StripeButtonManager> {
    if (buttons.isEmpty()) {
      return emptyList()
    }

    val tools = ArrayList<StripeButtonManager>(buttons.size)

    if (isNewStripes && (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT)) {
      if (separator.parent == null) {
        add(separator)
      }
      separator.isVisible = false
      separator.dndState = false
      separatorTopSide = false

      buttons.filterTo(tools) {
        val component = it.getComponent()
        component.isVisible || component === dragButton
      }
      tools.sortWith(stripeButtonManagerComparator)

      for ((index, tool) in tools.withIndex()) {
        if (tool.windowDescriptor.isSplit) {
          separator.isVisible = true
          tools.add(index, separatorStripe)
          break
        }
      }

      tools.remove(dragButton)

      if (separator.isVisible && tools.indexOf(separatorStripe) == 0) {
        if (processDrop) {
          separator.dndState = true
          separatorTopSide = true
        }
        else {
          separator.isVisible = false
          tools.remove(separatorStripe)
        }
      }

      if (!separator.isVisible && !tools.isEmpty() && processDrop) {
        separator.isVisible = true
        separator.dndState = true
        tools.add(separatorStripe)
      }
    }
    else {
      buttons.filterTo(tools) { it.getComponent().isVisible }
      tools.sortWith(stripeButtonManagerComparator)
    }

    return tools
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (!isFinishingDrop && isDroppingButton()) {
      g.color = if (isNewStripes) JBUI.CurrentTheme.ToolWindow.DragAndDrop.STRIPE_BACKGROUND else background.brighter()
      g.fillRect(0, 0, width, height)
      val rectangle = Rectangle(drawRectangle)
      if (!rectangle.isEmpty) {
        var round: Int? = null
        if (isNewStripes) {
          val size = JBUI.scale(30)
          rectangle.x += (rectangle.width - size) / 2
          rectangle.y += (rectangle.height - size) / 2
          rectangle.width = size
          rectangle.height = size
          round = JBUI.scale(8)
        }
        g.color = if (isNewStripes) JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_DROP_BACKGROUND else JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
        RectanglePainter.FILL.paint(g as Graphics2D, rectangle.x, rectangle.y, rectangle.width, rectangle.height, round)

        if (!isHorizontal() && separator.isVisible && separator.dndState) {
          val size = JBUI.scale(30)
          val x = (width - size) / 2
          val y = if (separatorTopSide) separator.y - (drawRectangle.height + size) / 2 else separator.y + separator.height + (drawRectangle.height - size) / 2

          g.stroke = BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
          g.color = JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_DROP_BORDER
          g.drawLine(x, y, x + size, y)
          g.drawLine(x + size, y, x + size, y + size)
          g.drawLine(x, y, x, y + size)
          g.drawLine(x + size, y + size, x, y + size)
        }
      }
    }
  }

  fun updatePresentation() {
    buttons.forEach(StripeButtonManager::updatePresentation)
  }
}