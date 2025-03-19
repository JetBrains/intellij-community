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
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ChangeListRemoveConfirmation {
  fun checkCanDeleteChangelist(project: Project,
                               list: LocalChangeList,
                               explicitly: Boolean): ThreeState {
    val activeVcss = ProjectLevelVcsManager.getInstance(project).allActiveVcss

    var confirmationAsked = false
    var removeVetoed = false

    for (vcs in activeVcss) {
      val permission = vcs.mayRemoveChangeList(list, explicitly)
      if (permission != ThreeState.UNSURE) {
        confirmationAsked = true
      }
      if (permission == ThreeState.NO) {
        removeVetoed = true
        break
      }
    }

    if (!confirmationAsked) {
      return ThreeState.UNSURE
    }
    else if (removeVetoed) {
      return ThreeState.NO
    }
    else {
      return ThreeState.YES
    }
  }

  @JvmStatic
  fun deleteEmptyInactiveLists(project: Project, lists: Collection<LocalChangeList>,
                               confirm: (toAsk: List<LocalChangeList>) -> Boolean) {
    val manager = ChangeListManager.getInstance(project)

    val toRemove = mutableListOf<LocalChangeList>()
    val toAsk = mutableListOf<LocalChangeList>()

    for (list in lists.mapNotNull { manager.getChangeList(it.id) }) {
      if (list.isDefault) continue

      when (checkCanDeleteChangelist(project, list, explicitly = false)) {
        ThreeState.UNSURE -> toAsk.add(list)
        ThreeState.YES -> toRemove.add(list)
        ThreeState.NO -> {
        }
      }
    }

    if (toAsk.isNotEmpty() && confirm(toAsk)) {
      toRemove.addAll(toAsk)
    }

    toRemove.forEach {
      manager.removeChangeList(it.name)
    }
  }
}