// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

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