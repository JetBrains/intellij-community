// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.changes

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls

@Serializable
sealed class ChangeListManagerState {
  @Serializable
  data object Default : ChangeListManagerState()
  @Serializable
  data object Updating : ChangeListManagerState()
  @Serializable
  data class Frozen(val reason: @Nls(capitalization = Nls.Capitalization.Sentence) String) : ChangeListManagerState()
}