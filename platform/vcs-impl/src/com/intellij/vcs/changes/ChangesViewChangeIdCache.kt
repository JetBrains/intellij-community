// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import org.jetbrains.annotations.ApiStatus

/**
 * Mapping of [ChangeId] to [Change] for changes currently visible in the Commit Changes View.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ChangesViewChangeIdCache(private val project: Project) {
  @Volatile
  private var changeListsChanges: Map<ChangeId, Change> = emptyMap()

  fun getChangeListChange(id: ChangeId): Change? {
    return changeListsChanges[id] ?: when (val commitPresentation = ChangesViewWorkflowManager.getInstance(project).editedCommit.value) {
      is EditedCommitDetails -> commitPresentation.changes.find { ChangeId.getId(it) == id }
      else -> null
    }
  }

  fun getEditedCommitDetailsChange(id: ChangeId): Change? {
    return when (val commitPresentation = ChangesViewWorkflowManager.getInstance(project).editedCommit.value) {
      is EditedCommitDetails -> commitPresentation.changes.find { ChangeId.getId(it) == id }
      else -> null
    }
  }

  fun updateChangeListsCache(allChanges: Iterable<Set<Change>>) {
    changeListsChanges = allChanges.asSequence().flatten().associateBy { ChangeId.getId(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangesViewChangeIdCache = project.service()
  }
}