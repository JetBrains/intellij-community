/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.Root
import java.awt.event.InputEvent

/**
 * @author Konstantin Bulenkov
 */
class OpenSelectedProjectsAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event)
    val newEvent = AnActionEvent(event.inputEvent, event.dataContext, event.place,
                                 event.presentation, event.actionManager, InputEvent.SHIFT_DOWN_MASK)
    when (item) {
      is RecentProjectGroupItem -> item.children.forEach { child -> child.openProject(newEvent) }
      is RecentProjectItem -> item.openProject(newEvent)
      is Root -> {}
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val item = getSelectedItem(event) ?: return

    if (ActionPlaces.WELCOME_SCREEN == event.place) {
      presentation.isEnabledAndVisible = true
      when (item) {
        is RecentProjectGroupItem -> presentation.setText(
          IdeBundle.messagePointer("action.presentation.OpenSelectedProjectsAction.text.open.all.projects.in.group")
        )
        else -> presentation.setText(IdeBundle.messagePointer("action.presentation.OpenSelectedProjectsAction.text.open.selected"))
      }

      return
    }

    presentation.isEnabledAndVisible = false
  }
}