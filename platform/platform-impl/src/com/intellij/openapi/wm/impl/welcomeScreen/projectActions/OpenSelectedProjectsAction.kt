// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import java.awt.event.InputEvent

/**
 * @author Konstantin Bulenkov
 */
class OpenSelectedProjectsAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event) ?: return
    val newEvent = AnActionEvent(event.inputEvent, event.dataContext, event.place,
                                 event.presentation, event.actionManager, InputEvent.SHIFT_DOWN_MASK)
    when (item) {
      is ProjectsGroupItem -> item.children.forEach { child -> child.openProject(newEvent) }
      is RecentProjectItem -> item.openProject(newEvent)
      else -> {}
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val item = getSelectedItem(event)

    if (item == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    if (ActionPlaces.WELCOME_SCREEN == event.place) {
      presentation.isEnabledAndVisible = item is RecentProjectItem || item is ProjectsGroupItem
      when (item) {
        is ProjectsGroupItem -> presentation.setText(
          IdeBundle.messagePointer("action.presentation.OpenSelectedProjectsAction.text.open.all.projects.in.group")
        )
        else -> presentation.setText(IdeBundle.messagePointer("action.presentation.OpenSelectedProjectsAction.text.open.selected"))
      }
    }
    else {
      presentation.isEnabledAndVisible = false
    }
  }
}