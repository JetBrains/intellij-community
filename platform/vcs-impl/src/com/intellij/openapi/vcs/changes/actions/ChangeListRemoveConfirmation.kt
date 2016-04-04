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
      val allIds = allLists.map { it.id }
      val confirmationAsked = hashSetOf<String>()
      val doNotRemove = hashSetOf<String>()

      val manager = ChangeListManager.getInstance(project)
      for (id in allIds) {
        for (vcs in ProjectLevelVcsManager.getInstance(project).allActiveVcss) {
          val list = manager.getChangeList(id)
          val permission = if (list == null) ThreeState.NO else vcs.mayRemoveChangeList(list, explicitly)
          if (permission != ThreeState.UNSURE) {
            confirmationAsked.add(id)
          }
          if (permission == ThreeState.NO) {
            doNotRemove.add(id)
            break
          }
        }
      }

      val toAsk = allIds.filter { it !in confirmationAsked && it !in doNotRemove }
      if (toAsk.isNotEmpty() && !ask.askIfShouldRemoveChangeLists(toAsk.map { manager.getChangeList(it) }.filterNotNull())) {
        doNotRemove.addAll(toAsk)
      }
      val toRemove = allIds.filter { it !in doNotRemove }.map { manager.getChangeList(it) }.filterNotNull()
      val active = toRemove.find { it.isDefault }
      toRemove.forEach { if (it != active) manager.removeChangeList(it.name) }

      if (active != null && RemoveChangeListAction.confirmActiveChangeListRemoval(project, listOf(active), active.getChanges().isEmpty())) {
        manager.removeChangeList(active.name)
      }
    }
  }
}