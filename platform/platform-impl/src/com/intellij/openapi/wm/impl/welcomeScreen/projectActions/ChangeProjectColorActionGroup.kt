// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColorChooserService
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

class ChangeProjectColorActionGroup: DefaultActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val projectPath = e?.project?.basePath ?: return emptyArray()

    return arrayOf(ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Amber.title"), 0),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Rust.title"), 1),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Olive.title"), 2),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Grass.title"), 8),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Ocean.title"), 7),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Sky.title"), 3),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Cobalt.title"), 4),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Violet.title"), 6),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Plum.title"), 5),
                   Separator(),
                   ChooseCustomProjectColorAction()
                   )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }
}

class ChangeProjectColorAction(val projectPath: String, val name: @NlsSafe String, val index: Int):
  AnAction(name, "", RecentProjectIconHelper.generateProjectIcon(projectPath, true, size = 16, colorIndex = index)), DumbAware
{
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = "$name$nameSuffix"
  }

  private val nameSuffix: String get() {
    val customizer = ProjectWindowCustomizerService.getInstance()
    if (index == customizer.getCurrentProjectColorIndex(projectPath)) {
      return " (${IdeBundle.message("action.ChangeProjectColorAction.Current.title")})"
    }
    return ""
  }

  override fun actionPerformed(e: AnActionEvent) {
    val customizer = ProjectWindowCustomizerService.getInstance()
    e.project?.let { customizer.clearToolbarColorsAndInMemoryCache(it) }
    customizer.setAssociatedColorsIndex(projectPath, index)
    LafManager.getInstance().updateUI()
  }
}

class ChooseCustomProjectColorAction: AnAction(IdeBundle.message("action.ChooseCustomProjectColorAction.title")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val ideFrame = IdeFocusManager.getInstance(project).lastFocusedFrame
    var relativePoint: RelativePoint? = null
    if (ideFrame != null) {
      relativePoint = RelativePoint(ideFrame.component, Point(200, 30))
    }

    ColorChooserService.instance.showPopup(project = project,
                                           currentColor = ProjectWindowCustomizerService.getInstance().getToolbarBackground(project),
                                           listener = { color, _ ->
                                             ProjectWindowCustomizerService.getInstance().setProjectCustomColor(project, color)
                                             LafManager.getInstance().updateUI()
                                           },
                                           location = relativePoint)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }
}
