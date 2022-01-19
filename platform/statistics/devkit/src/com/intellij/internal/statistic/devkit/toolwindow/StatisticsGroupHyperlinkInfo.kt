// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.ide.DataManager
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint

internal class StatisticsGroupHyperlinkInfo(private val groupId: String,
                                            private val eventId: String,
                                            private val eventData: String,
                                            private val file: VirtualFile,
                                            private val lineNumber: Int) : HyperlinkInfo {
  override fun navigate(project: Project) {
    val actions = StatisticsLogGroupActionsProvider.EP_NAME.extensionList
      .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
      .flatMap { it.getActions(groupId, eventId, eventData) }
    if (actions.isEmpty()) {
      OpenFileHyperlinkInfo(project, file, lineNumber).navigate(project)
    }
    else {
      val group = DefaultActionGroup()
      group.addAll(actions)
      group.add(OpenGroupScheme(project, file, lineNumber))
      showPopup(project, group)
    }
  }

  private fun showPopup(project: Project?, actionGroup: ActionGroup) {
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup, dataContext,
                                                                      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
      val frame = WindowManager.getInstance().getFrame(project)
      if (frame != null) {
        val mousePosition = frame.mousePosition
        if (mousePosition != null) {
          popup.show(RelativePoint(frame, mousePosition))
        }
      }
    }
  }

  class OpenGroupScheme(private val project: Project, private val file: VirtualFile, private val lineNumber: Int)
    : AnAction(StatisticsBundle.message("stats.navigate.to.group.scheme")) {
    override fun actionPerformed(e: AnActionEvent) {
      OpenFileHyperlinkInfo(project, file, lineNumber).navigate(project)
    }
  }
}