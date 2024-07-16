// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.component

import com.intellij.collaboration.util.ComputedResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal interface LabeledListPanelViewModel<T> {
  val isEditingAllowed: Boolean
  val items: StateFlow<List<T>>

  // convert to batches
  val selectableItems: StateFlow<ComputedResult<List<T>>>
  val adjustmentProcessState: StateFlow<ComputedResult<Unit>?>

  val editRequests: SharedFlow<Unit>

  fun requestEdit()

  fun adjustList(newList: List<T>)
}