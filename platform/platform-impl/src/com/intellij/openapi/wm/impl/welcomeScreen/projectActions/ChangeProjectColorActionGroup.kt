// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.impl.islands.isColorIslandGradientAvailable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ColorChooserService
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBPoint
import java.nio.file.Path

class ChangeProjectColorActionGroup: DefaultActionGroup(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return emptyArray()
    val projectPath = ProjectWindowCustomizerService.projectPath(project) ?: return emptyArray()
    val projectName = if (RecentProjectsManagerBase.getInstanceEx().hasCustomIcon(project)) "" else project.name

    return arrayOf(ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Amber.title"), 0, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Rust.title"), 1, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Olive.title"), 2, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Grass.title"), 8, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Ocean.title"), 7, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Sky.title"), 3, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Cobalt.title"), 4, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Violet.title"), 6, projectName),
                   ChangeProjectColorAction(projectPath, IdeBundle.message("action.ChangeProjectColorAction.Plum.title"), 5, projectName),
                   Separator(),
                   ChooseCustomProjectColorAction()
                   )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null
    e.presentation.icon = project?.let {
      val projectPath = ProjectWindowCustomizerService.projectPath(project) ?: return@let null
      RecentProjectIconHelper.generateNewProjectIcon(projectPath, true, projectName = "", colorIndex = null, size = 14)
    }
    e.presentation.putClientProperty(ActionUtil.SHOW_ICON_IN_MAIN_MENU, true)
  }
}

internal class ChangeProjectColorAction(
  projectPath: Path,
  val name: @NlsSafe String,
  val index: Int,
  projectName: String?,
) : AnAction(
  name,
  "",
  RecentProjectIconHelper.generateNewProjectIcon(projectPath, true, projectName = projectName, colorIndex = index, size = 16)
), DumbAware
{
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

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
    repaintFrame(project)
  }
}

internal class ChooseCustomProjectColorAction: AnAction(IdeBundle.message("action.ChooseCustomProjectColorAction.title")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val ideFrame = IdeFocusManager.getInstance(project).lastFocusedFrame
    var relativePoint: RelativePoint? = null
    if (ideFrame != null) {
      relativePoint = RelativePoint(ideFrame.component, JBPoint(200, 30))
    }

    ColorChooserService.getInstance().showPopup(
      project = project,
      currentColor = ProjectWindowCustomizerService.getInstance().getProjectColorToCustomize(project),
      listener = { color, _ ->
        ProjectWindowCustomizerService.getInstance().setCustomProjectColor(project, color)
        repaintFrame(e.project)
      },
      location = relativePoint,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && !isColorIslandGradientAvailable()
  }
}

internal fun repaintFrame(project: Project?) {
  WindowManager.getInstance().getIdeFrame(project)?.component?.repaint()
}
