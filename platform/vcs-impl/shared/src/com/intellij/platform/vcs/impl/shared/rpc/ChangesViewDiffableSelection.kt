// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the currently selected change in the Changes View
 * and the changes below and above it, which can be navigated using
 * "Next/Previous Difference" or "Compare Previous/Next File" action
 */
@ApiStatus.Internal
@Serializable
data class ChangesViewDiffableSelection(
  val selectedChange: ChangesTreePath,
  val previousChange: ChangesTreePath?,
  val nextChange: ChangesTreePath?,
)
