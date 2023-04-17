// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.openapi.vcs.changes.Change
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GitLabMergeRequestDiffBridge {
  private val _displayedChanges = MutableSharedFlow<ChangesSelection>(1)
  val displayedChanges: Flow<ChangesSelection> = _displayedChanges.asSharedFlow()

  private val _selectedChange = MutableSharedFlow<Change?>(1)
  val selectedChange: Flow<Change?> = _selectedChange.asSharedFlow()

  fun setChanges(changes: ChangesSelection) {
    _displayedChanges.tryEmit(changes)
    _selectedChange.tryEmit((changes as? ChangesSelection.Single)?.selectedChange)
  }

  fun changeSelected(change: Change?) {
    _selectedChange.tryEmit(change)
  }
}
