// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.util.ChangesSelection
import kotlinx.coroutines.flow.StateFlow

interface GHPRCombinedDiffSelectionModel {
  val changesSelection: StateFlow<ChangesSelection?>
  fun updateSelectedChanges(selection: ChangesSelection?)
}

