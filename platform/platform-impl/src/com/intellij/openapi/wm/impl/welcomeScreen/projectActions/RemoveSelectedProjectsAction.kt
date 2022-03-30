// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.Root

/**
 * @author Konstantin Bulenkov
 */
internal class RemoveSelectedProjectsAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(event: AnActionEvent) {
    val manager = RecentProjectsManager.getInstance()
    when (val item = getSelectedItem(event)) {
      is RecentProjectGroupItem -> manager.removeGroup(item.group)
      is RecentProjectItem -> manager.removePath(item.projectPath)
      is Root -> {}
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getSelectedItem(event) != null
  }
}