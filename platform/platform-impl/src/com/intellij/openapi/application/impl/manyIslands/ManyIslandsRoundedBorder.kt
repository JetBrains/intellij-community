// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.manyIslands

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.toolWindow.xNext.island.XNextRoundedBorder
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Paint
import javax.swing.JComponent

internal class ManyIslandsRoundedBorder(fillColor: (JComponent) -> Paint?) :
  XNextRoundedBorder(
    fillColor,
    fillColor,
    { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) },
    { c: JComponent -> InternalUICustomization.getInstance()?.getCustomMainBackgroundColor() },
    JBUI.getInt("Island.arc", 10),
    JBUI.scale(2),
    JBInsets.create("Island.border1.insets", JBInsets(4, 4, 4, 4)),
    JBInsets.create("Island.border2.insets", JBInsets(2, 2, 2, 2))) {

  companion object {
    fun createToolWindowBorder(component: JComponent, child: JComponent) {
      component.border = ManyIslandsRoundedBorder { child.background }
    }

    fun createEditorBorder(component: EditorsSplitters) {
      component.border = ManyIslandsRoundedBorder {
        UIUtil.findComponentOfType(it, JBEditorTabs::class.java)?.background ?: it.background
      }
    }
  }
}