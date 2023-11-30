// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.util.ChangesSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GHPRCombinedDiffSelectionModelImpl() : GHPRCombinedDiffSelectionModel {
  private val _changesSelection = MutableStateFlow<ChangesSelection?>(null)
  override val changesSelection: StateFlow<ChangesSelection?> = _changesSelection.asStateFlow()

  override fun updateSelectedChanges(selection: ChangesSelection?) {
    _changesSelection.value = selection
  }
}