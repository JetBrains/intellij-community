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
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil

abstract class ChangeListRemoveConfirmation() {
  
  abstract fun askIfShouldRemoveChangeLists(ask: List<LocalChangeList>): Boolean
  
  companion object {
    @JvmStatic
    fun processLists(project: Project, explicitly: Boolean, allLists: Collection<LocalChangeList>, ask: ChangeListRemoveConfirmation) {
      val confirmationAsked = ContainerUtil.newIdentityTroveSet<LocalChangeList>()
      val doNotRemove = ContainerUtil.newIdentityTroveSet<LocalChangeList>()

      for (list in allLists) {
        for (vcs in ProjectLevelVcsManager.getInstance(project).getAllActiveVcss()) {
          val permission = vcs.mayRemoveChangeList(list, explicitly)
          if (permission != ThreeState.UNSURE) {
            confirmationAsked.add(list)
          }
          if (permission == ThreeState.NO) {
            doNotRemove.add(list)
            break
          }
        }
      }

      val toAsk = allLists.filter { it !in confirmationAsked && it !in doNotRemove }
      if (toAsk.isNotEmpty() && !ask.askIfShouldRemoveChangeLists(toAsk)) {
        doNotRemove.addAll(toAsk)
      }
      val toRemove = allLists.filter { it !in doNotRemove }
      val active = toRemove.find { it.isDefault() }
      toRemove.forEach { if (it != active) ChangeListManager.getInstance(project).removeChangeList(it.getName()) }

      if (active != null && RemoveChangeListAction.confirmActiveChangeListRemoval(project, listOf(active), active.getChanges().isEmpty())) {
        ChangeListManager.getInstance(project).removeChangeList(active.getName())
      }
    }
  }
}