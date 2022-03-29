// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages

/**
 * @author Konstantin Bulenkov
 */
class CreateNewProjectGroupAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val validator = object : InputValidator {
      override fun checkInput(inputString: String): Boolean {
        val text = inputString.trim()
        return getGroup(text) == null
      }

      override fun canClose(inputString: String): Boolean {
        return true
      }
    }

    val newGroup = Messages.showInputDialog(null as Project?, IdeBundle.message("dialog.message.project.group.name"),
                                            IdeBundle.message("dialog.title.create.new.project.group"), null, null, validator)
    if (newGroup != null) {
      RecentProjectsManager.getInstance().addGroup(ProjectGroup(newGroup))
    }
  }

  companion object {
    private fun getGroup(name: String): ProjectGroup? {
      for (group in RecentProjectsManager.getInstance().groups) {
        if (group.name == name) {
          return group
        }
      }

      return null
    }
  }
}