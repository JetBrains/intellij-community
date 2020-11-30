// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel

object OpenReviewButton {
  fun createOpenReviewButton(@NlsContexts.Tooltip tooltip: String? = null): JPanel = JPanel(GridBagLayout()).apply {
    isOpaque = false
    background = JBUI.CurrentTheme.ActionButton.pressedBackground()
    add(InlineIconButton(AllIcons.General.ArrowRight).apply {
      toolTipText = tooltip
    })
  }

  fun installOpenButtonListeners(list: JBList<*>,
                                 openButtonViewModel: OpenReviewButtonViewModel,
                                 onClickActionProvider: () -> AnAction) {

    list.addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val point = e.point
        var index = list.locationToIndex(point)
        val cellBounds = list.getCellBounds(index, index)
        if (cellBounds == null || !cellBounds.contains(point)) index = -1

        openButtonViewModel.hoveredRowIndex = index
        openButtonViewModel.isButtonHovered = if (index == -1) false else isInsideButton(cellBounds, point)
        list.repaint()
      }
    })

    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        val point = event.point
        val index = list.locationToIndex(point)
        val cellBounds = list.getCellBounds(index, index)
        if (cellBounds == null || !cellBounds.contains(point)) return false

        if (isInsideButton(cellBounds, point)) {
          val action = onClickActionProvider()
          ActionUtil.invokeAction(action, list, ActionPlaces.UNKNOWN, event, null)
          return true
        }
        return false
      }
    }.installOn(list)
  }

  private fun isInsideButton(cellBounds: Rectangle, point: Point): Boolean {
    val iconSize = EmptyIcon.ICON_16.iconWidth
    val rendererRelativeX = point.x - cellBounds.x
    return (cellBounds.width - rendererRelativeX) <= iconSize
  }
}

class OpenReviewButtonViewModel {
  var hoveredRowIndex: Int = -1
  var isButtonHovered: Boolean = false
}