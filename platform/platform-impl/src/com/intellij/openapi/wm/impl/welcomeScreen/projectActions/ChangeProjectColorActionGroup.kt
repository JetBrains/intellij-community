// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ColorChooserService
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBPoint

class ChangeProjectColorActionGroup: DefaultActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val projectPath = e?.project?.let { ProjectWindowCustomizerService.projectPath(it) } ?: return emptyArray()

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
    val project = e.project ?: return
    e.presentation.text = transformToCurrentIfNeeded(name, project)
  }

  @NlsActions.ActionText
  private fun transformToCurrentIfNeeded(@NlsActions.ActionText name: String, project: Project): String {
    val customizer = ProjectWindowCustomizerService.getInstance()
    if (index == customizer.getCurrentProjectColorIndex(project)) {
      return IdeBundle.message("action.ChangeProjectColorAction.Current.title", name)
    }
    return name
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val customizer = ProjectWindowCustomizerService.getInstance()
    customizer.clearToolbarColorsAndInMemoryCache(project)
    customizer.setAssociatedColorsIndex(project, index)
    project.repaintFrame()
  }
}

class ChooseCustomProjectColorAction: AnAction(IdeBundle.message("action.ChooseCustomProjectColorAction.title")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val ideFrame = IdeFocusManager.getInstance(project).lastFocusedFrame
    var relativePoint: RelativePoint? = null
    if (ideFrame != null) {
      relativePoint = RelativePoint(ideFrame.component, JBPoint(200, 30))
    }

    ColorChooserService.instance.showPopup(project = project,
                                           currentColor = ProjectWindowCustomizerService.getInstance().getToolbarBackground(project),
                                           listener = { color, _ ->
                                             ProjectWindowCustomizerService.getInstance().setProjectCustomColor(project, color)
                                             e.project?.repaintFrame()
                                           },
                                           location = relativePoint)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }
}

private fun Project.repaintFrame() {
  WindowManager.getInstance().getIdeFrame(this)?.component?.repaint()
}
