// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.ListSelection
import com.intellij.openapi.diff.DiffBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Number of files reachable from the diff toolbar and the 0-based position of the current one among them.
 */
@ApiStatus.Internal
data class DiffFilesCounterState(val changesCount: Int, val selectedIndex: Int) {
  constructor(changes: ListSelection<*>) : this(changes.list.size, changes.selectedIndex)

  val hasMultipleFiles: Boolean get() = changesCount > 1

}

@get:ApiStatus.Internal
val DiffFilesCounterState.text: @Nls String get() = DiffBundle.message("diff.files.count.progress", changesCount, selectedIndex + 1)
