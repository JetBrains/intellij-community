// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.ide.getProjectNameForIcon
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe

class ChangeProjectColorActionGroup: DefaultActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val projectName = e?.project?.let { getProjectNameForIcon(it) } ?: return emptyArray()

    return arrayOf(ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Amber.title"), 0),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Rust.title"), 1),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Olive.title"), 2),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Grass.title"), 8),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Ocean.title"), 7),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Sky.title"), 3),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Cobalt.title"), 4),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Violet.title"), 6),
                   ChangeProjectColorAction(projectName, IdeBundle.message("action.ChangeProjectColorAction.Plum.title"), 5))
  }
}

class ChangeProjectColorAction(val projectName: String, val name: @NlsSafe String, val index: Int):
  AnAction(name, "", RecentProjectIconHelper.generateProjectIcon(projectName, true, size = 16, colorIndex = index)), DumbAware
{
  override fun actionPerformed(e: AnActionEvent) {
    val customizer = ProjectWindowCustomizerService.getInstance()
    customizer.setAssociatedColorsIndex(projectName, index)
    e.project?.let { customizer.clearCustomColorsAndCache(it) }
    LafManager.getInstance().updateUI()
  }
}