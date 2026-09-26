// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the currently selected change in the Changes View,
 * the changes that can be navigated using "Next/Previous Difference" and "Compare Previous/Next File" actions,
 * and the values shown by the diff toolbar file counter.
 *
 * Everything here is computed by the frontend, which owns the changes tree, in tree display order.
 *
 * [previousChange]/[nextChange] are the files reachable with "Compare Previous/Next File". They are scoped to the
 * explicit selection when 2+ files are selected, as in monolith mode, and span the whole tree otherwise.
 *
 * [selectedIndex]/[changesCount] are the file counter values: the 0-based position of [selectedChange] among
 * [changesCount] changes. With `vcs.diff.preview.scope.navigation.to.group` enabled they are counted within the
 * explicit selection of 2+ files, or, for a single selection, within the group of the selection (changelist or
 * unversioned files); otherwise over all changes. Note the counter for a single selection is intentionally narrower
 * than the navigation above – the same mismatch exists in monolith mode.
 *
 * Both scopes are resolved on the frontend, which owns the tree – only the resulting paths and numbers are sent here.
 *
 * [selectedIndex] is `null` and [changesCount] is `0` when the position cannot be determined (e.g. for a hijacked
 * change, which has no corresponding tree node); the counter is then hidden.
 *
 * The whole value is `null` when no diffable file is selected, e.g. for a changelist with no files. The counter then
 * shows "No files", as monolith mode does.
 */
@ApiStatus.Internal
@Serializable
data class ChangesViewDiffableSelection(
  val selectedChange: ChangesTreePath,
  val previousChange: ChangesTreePath?,
  val nextChange: ChangesTreePath?,
  val selectedIndex: Int? = null,
  val changesCount: Int = 0,
)
