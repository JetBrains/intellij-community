/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.util.ThreeState

abstract class ChangeListRemoveConfirmation() {
  
  abstract fun askIfShouldRemoveChangeLists(ask: List<LocalChangeList>): Boolean
  
  companion object {
    @JvmStatic
    fun processLists(project: Project, explicitly: Boolean, allLists: Collection<LocalChangeList>, ask: ChangeListRemoveConfirmation) {
      val manager = ChangeListManager.getInstance(project)
      val activeVcss = ProjectLevelVcsManager.getInstance(project).allActiveVcss

      val toAsk = hashSetOf<String>()
      val toRemove = hashSetOf<String>()

      for (id in allLists.map { it.id }) {
        var confirmationAsked = false
        var removeVetoed = false

        for (vcs in activeVcss) {
          val list = manager.getChangeList(id)
          val permission = if (list == null) ThreeState.NO else vcs.mayRemoveChangeList(list, explicitly)
          if (permission != ThreeState.UNSURE) {
            confirmationAsked = true
          }
          if (permission == ThreeState.NO) {
            removeVetoed = true
            break
          }
        }

        if (!confirmationAsked) {
          toAsk.add(id)
        }
        else if (!removeVetoed) {
          toRemove.add(id)
        }
      }

      val toAskLists = toAsk.mapNotNull { manager.getChangeList(it) }
      if (toAskLists.isNotEmpty() && ask.askIfShouldRemoveChangeLists(toAskLists)) {
        toRemove.addAll(toAsk)
      }

      val toRemoveLists = toRemove.mapNotNull { manager.getChangeList(it) }
      val active = toRemoveLists.find { it.isDefault }
      toRemoveLists.forEach { if (it != active) manager.removeChangeList(it.name) }

      if (active != null && RemoveChangeListAction.confirmActiveChangeListRemoval(project, listOf(active), active.getChanges().isEmpty())) {
        manager.removeChangeList(active.name)
      }
    }
  }
}