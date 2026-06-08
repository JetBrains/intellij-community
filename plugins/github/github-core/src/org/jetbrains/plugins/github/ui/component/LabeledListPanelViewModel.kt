// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.component

import com.intellij.collaboration.util.IncrementallyComputedValue
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LabeledListPanelViewModel<T> {
  val items: StateFlow<List<T>>
  val selectableItems: StateFlow<IncrementallyComputedValue<List<T>>>

  fun adjustList(newList: List<T>)
}