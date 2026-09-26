// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx.ICON_CENTER_POSITION
import com.intellij.openapi.editor.ex.EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR
import com.intellij.openapi.editor.ex.util.EditorUtil.getDefaultCaretWidth
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupFactoryImpl
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import kotlin.math.max
import kotlin.math.min

fun getBestPopupPosition(context: DataContext): RelativePoint {
  return getBestPopupPositionInsideGutter(context)
         ?: getBestPopupPositionInsideComponent(context)
}

fun getBestBalloonPosition(context: DataContext): RelativePoint {
  return getBestBalloonPositionInsideGutter(context)
         ?: getBestBalloonPositionInsideEditor(context)
         ?: getBestBalloonPositionInsideList(context)
         ?: getBestBalloonPositionInsideTree(context)
         ?: getBestBalloonPositionInsideTable(context)
         ?: getBestBalloonPositionInsideComponent(context)
}

private fun getBestPopupPositionInsideGutter(context: DataContext): RelativePoint? {
  return getBestPositionInsideGutter(context, Rectangle::bottomCenter)
}

private fun getBestPopupPositionInsideComponent(context: DataContext): RelativePoint {
  return JBPopupFactory.getInstance().guessBestPopupLocation(context)
}

private fun getBestBalloonPositionInsideGutter(context: DataContext): RelativePoint? {
  return getBestPositionInsideGutter(context, Rectangle::topCenter)
}

private fun getBestPositionInsideGutter(context: DataContext, location: Rectangle.() -> Point): RelativePoint? {
  val component = getFocusComponent<EditorGutterComponentEx>(context) ?: return null
  val editor = CommonDataKeys.EDITOR.getData(context) ?: return null
  val logicalLine = context.getData(LOGICAL_LINE_AT_CURSOR) ?: return null
  val iconCenterPosition = context.getData(ICON_CENTER_POSITION) ?: return null
  val renderer = component.getGutterRenderer(iconCenterPosition) ?: return null
  val visibleArea = component.visibleRect
  val x = iconCenterPosition.x - renderer.icon.iconWidth / 2
  val linePoint = editor.logicalPositionToXY(LogicalPosition(logicalLine,0))
  val rect = Rectangle(x, linePoint.y, renderer.icon.iconWidth, editor.lineHeight)
  if (!visibleArea.contains(rect)) component.scrollRectToVisible(rect)
  return RelativePoint(component, rect.location())
}

private fun getBestBalloonPositionInsideEditor(context: DataContext): RelativePoint? {
  val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context)
  val editor = CommonDataKeys.EDITOR.getData(context) ?: return null
  val contentComponent = editor.contentComponent
  if (contentComponent !== component) return null
  val caretVisualPosition = editor.getCaretVisualPosition()
  val caretPosition = editor.visualPositionToXY(caretVisualPosition)
  val visibleArea = editor.scrollingModel.visibleArea
  val rect = Rectangle(caretPosition, Dimension(getDefaultCaretWidth(), editor.lineHeight))
  if (!visibleArea.contains(rect)) component.scrollRectToVisible(rect)
  return RelativePoint(contentComponent, caretPosition)
}

private fun Editor.getCaretVisualPosition(): VisualPosition {
  val anchorPosition = getUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION)
  if (anchorPosition != null) return anchorPosition
  if (caretModel.isUpToDate) return caretModel.visualPosition
  return offsetToVisualPosition(caretModel.offset)
}

private fun getBestBalloonPositionInsideList(context: DataContext): RelativePoint? {
  val component = getFocusComponent<JList<*>>(context) ?: return null
  val visibleRect = component.visibleRect
  val firstVisibleIndex = component.firstVisibleIndex
  val lastVisibleIndex = component.lastVisibleIndex
  val selectedIndices = component.selectedIndices
  for (index in selectedIndices) {
    if (index in firstVisibleIndex..lastVisibleIndex) {
      val cellBounds = component.getCellBounds(index, index)
      val position = Point(visibleRect.x + visibleRect.width / 4, cellBounds.y)
      return RelativePoint(component, position)
    }
  }
  return null
}

private fun getBestBalloonPositionInsideTree(context: DataContext): RelativePoint? {
  val component = getFocusComponent<JTree>(context) ?: return null
  val visibleRect = component.visibleRect
  val selectionRows = component.selectionRows ?: return null
  for (row in selectionRows.sorted()) {
    val rowBounds = component.getRowBounds(row)
    if (!visibleRect.contains(rowBounds)) continue
    return RelativePoint(component, rowBounds.topCenter())
  }
  val visibleCenter = visibleRect.center()
  val distance = { it: Int -> visibleCenter.distance(component.getRowBounds(it).center()) }
  val nearestRow = selectionRows.sortedBy(distance).firstOrNull() ?: return null
  val rowBounds = component.getRowBounds(nearestRow)
  val dimension = Dimension(min(visibleRect.width, rowBounds.width), rowBounds.height)
  component.scrollRectToVisible(Rectangle(rowBounds.position(), dimension))
  return RelativePoint(component, rowBounds.topCenter())
}

private fun getBestBalloonPositionInsideTable(context: DataContext): RelativePoint? {
  val component = getFocusComponent<JTable>(context) ?: return null
  val visibleRect = component.visibleRect
  val column = component.columnModel.selectionModel.leadSelectionIndex
  val row = max(component.selectionModel.leadSelectionIndex, component.selectionModel.anchorSelectionIndex)
  val rect = component.getCellRect(row, column, false)
  if (!visibleRect.intersects(rect)) component.scrollRectToVisible(rect)
  return RelativePoint(component, rect.position())
}

private fun getBestBalloonPositionInsideComponent(context: DataContext): RelativePoint {
  val component = getFocusComponent<JComponent>(context)!!
  return RelativePoint(component, component.visibleRect.center())
}

private inline fun <reified C : JComponent> getFocusComponent(context: DataContext): C? {
  val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context)
  if (component is C) return component
  val project = CommonDataKeys.PROJECT.getData(context)
  val frame = project?.let { WindowManager.getInstance().getFrame(it) }
  val focusOwner = frame?.rootPane
  if (focusOwner is C) return focusOwner
  return null
}

private fun Rectangle.topCenter() = Point(centerX.toInt(), y)

private fun Rectangle.bottomCenter() = Point(centerX.toInt(), y + height)

private fun Rectangle.center() = Point(centerX.toInt(), centerY.toInt())

private fun Rectangle.position() = Point(x, y)