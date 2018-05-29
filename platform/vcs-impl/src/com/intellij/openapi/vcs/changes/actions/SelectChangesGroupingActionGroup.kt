// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import java.awt.Graphics

class SelectChangesGroupingActionGroup : DefaultActionGroup(), DumbAware {
  override fun canBePerformed(context: DataContext): Boolean = true

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(ChangesGroupingSupport.KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val group = DefaultActionGroup().apply {
      addSeparator(e.presentation.text)
      addAll(this@SelectChangesGroupingActionGroup)
    }

    val popup = SelectChangesGroupingActionPopup(group, e.dataContext)
    val component = e.inputEvent.component
    when (component) {
      is ActionButtonComponent -> popup.showUnderneathOf(component)
      else -> popup.showInCenterOf(component)
    }
  }
}

private class SelectChangesGroupingActionPopup(group: ActionGroup, dataContext: DataContext) : PopupFactoryImpl.ActionGroupPopup(
  null, group, dataContext, false, false, false, true, null, -1, null, null) {
  override fun getListElementRenderer() = object : PopupListElementRenderer<Any>(this) {
    override fun createSeparator() = object : SeparatorWithText() {
      init {
        textForeground = JBColor.BLACK
        setCaptionCentered(false)
      }

      override fun paintLine(g: Graphics, x: Int, y: Int, width: Int) = Unit
    }
  }
}