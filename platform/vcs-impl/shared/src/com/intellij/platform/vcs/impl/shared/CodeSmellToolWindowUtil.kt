// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.content.impl.ContentImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val CODE_SMELL_DETECTOR_KEY: Key<Boolean> = Key.create("CODE_SMELL_DETECTOR_KEY")

@ApiStatus.Internal
fun showCodeSmellsPanelInToolWindow(project: Project, panel: NewErrorTreeViewPanel) : Boolean {
  val toolWindow = ProblemsView.getToolWindow(project)
  if (toolWindow != null && toolWindow.isAvailable) {
    toolWindow.activate({
      val contentManager = toolWindow.contentManager

      for (oldContent in contentManager.contents) {
        if (oldContent.isPinned) continue
        if (oldContent.getUserData(CODE_SMELL_DETECTOR_KEY) == true) {
          contentManager.removeContent(oldContent, true)
        }
      }

      val content = ContentImpl(panel, VcsBundle.message("code.smells.error.messages.tab.name"), true)
      content.putUserData(CODE_SMELL_DETECTOR_KEY, true)
      contentManager.addContent(content)
      contentManager.setSelectedContent(content, true)
    }, true, true)

    return true;
  }

  return false;
}
