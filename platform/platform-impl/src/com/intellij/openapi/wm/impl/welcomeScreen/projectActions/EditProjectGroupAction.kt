// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.util.asSafely

/**
 * @author Konstantin Bulenkov
 */
class EditProjectGroupAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(event: AnActionEvent) {
    val group = getSelectedItem(event).asSafely<ProjectsGroupItem>() ?: return
    val tree = getTree(event) ?: return
    val name = Messages.showInputDialog(tree, IdeBundle.message("label.enter.group.name"),
                                        IdeBundle.message("dialog.title.change.group.name"), null, group.displayName(),
                                        object : InputValidatorEx {
                                          override fun getErrorText(inputString: String): String? {
                                            val text = inputString.trim()
                                            if (text.isEmpty()) return IdeBundle.message("error.name.cannot.be.empty")
                                            if (!checkInput(text)) return IdeBundle.message("error.group.already.exists", text)
                                            return null
                                          }

                                          override fun checkInput(inputString: String): Boolean {
                                            val text = inputString.trim()
                                            if (text == group.displayName()) return true

                                            for (projectGroup in RecentProjectsManager.getInstance().groups) {
                                              if (projectGroup.name == inputString) {
                                                return false
                                              }
                                            }

                                            return true
                                          }
                                        })

    if (name != null) {
      group.group.name = name
    }
  }

  override fun update(event: AnActionEvent) {
    val item = getSelectedItem(event)
    event.presentation.isEnabledAndVisible = item is ProjectsGroupItem
  }
}