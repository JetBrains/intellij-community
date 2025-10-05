// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.isInternal
import com.intellij.toolWindow.xNext.island.XNextRoundedBorder
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Paint
import javax.swing.JComponent

internal open class IslandsRoundedBorder(fillColor: (JComponent) -> Paint?) :
  XNextRoundedBorder(
    fillColor,
    fillColor,
    { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) },
    { _: JComponent -> InternalUICustomization.getInstance()?.getCustomMainBackgroundColor() },
    JBUI.getInt("Island.arc", 10),
    JBUI.scale(2),
    JBInsets.create("Island.border1.insets", JBInsets(4, 4, 4, 4)),
    JBInsets.create("Island.border2.insets", JBInsets(3, 3, 3, 3))) {

  companion object {
    fun createToolWindowBorder(toolwindow: ToolWindow, component: JComponent) {
      component.border = object : IslandsRoundedBorder({ it.background }) {
        override fun getBorderInsets(c: Component): Insets? {
          if (!toolwindow.type.isInternal) {
            return JBInsets.emptyInsets()
          }
          return super.getBorderInsets(c)
        }
      }
    }

    fun createEditorBorder(editorsSplitters: EditorsSplitters, editorTabPainter: TabPainterAdapter) {
      editorsSplitters.border = IslandsRoundedBorder { editorTabPainter.tabPainter.getBackgroundColor() }
    }

    fun paintBeforeEditorEmptyText(component: JComponent, graphics: Graphics, editorTabPainter: TabPainterAdapter) {
      val g = IdeBackgroundUtil.getOriginalGraphics(graphics)
      g.color = editorTabPainter.tabPainter.getBackgroundColor()
      g.fillRect(0, 0, component.width, component.height)
    }
  }
}