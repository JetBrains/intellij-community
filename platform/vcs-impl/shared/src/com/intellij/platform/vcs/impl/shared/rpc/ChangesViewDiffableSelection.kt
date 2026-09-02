// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the currently selected change in the Changes View
 * and the changes that can be navigated using "Next/Previous Difference" and "Compare Previous/Next File" actions.
 *
 * Everything here is computed by the frontend, which owns the changes tree, in tree display order.
 *
 * [previousChange]/[nextChange] are the files reachable with "Compare Previous/Next File". They are scoped to the
 * explicit selection when 2+ files are selected, as in monolith mode, and span the whole tree otherwise.
 */
@ApiStatus.Internal
@Serializable
data class ChangesViewDiffableSelection(
  val selectedChange: ChangesTreePath,
  val previousChange: ChangesTreePath?,
  val nextChange: ChangesTreePath?,
)
