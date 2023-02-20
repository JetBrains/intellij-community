// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.openapi.ListSelection
import com.intellij.openapi.vcs.changes.Change
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GitLabMergeRequestDiffBridge {
  private val _changes = MutableStateFlow<ListSelection<Change>>(ListSelection.empty())
  val changes: StateFlow<ListSelection<Change>> = _changes.asStateFlow()

  private val _selectedChange = MutableStateFlow<Change?>(null)
  val selectedChange: StateFlow<Change?> = _selectedChange.asStateFlow()

  fun setChanges(changes: ListSelection<Change>) {
    _changes.value = changes
  }

  fun changeSelected(change: Change?) {
    _selectedChange.value = change
  }
}



