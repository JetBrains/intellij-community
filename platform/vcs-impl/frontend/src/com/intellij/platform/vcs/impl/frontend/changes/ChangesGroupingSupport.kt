// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTreeGroupingUpdateScheduler
import com.intellij.platform.vcs.impl.shared.rpc.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeSupport

@ApiStatus.Internal
open class ChangesGroupingSupport(val project: Project, private val source: String, private val cs: CoroutineScope) {
  private val changeSupport = PropertyChangeSupport(source)
  private val groupingUpdateScheduler = ShelfTreeGroupingUpdateScheduler.getInstance(project)
  private val groupingStatesHolder = ChangesGroupingStatesHolder.getInstance(project)
  private val groupingKeys get() = groupingStatesHolder.getGroupingsForPlace(source)

  private val updateFlow = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch {
      updateFlow.collectLatest {
        val newValue = groupingStatesHolder.getGroupingsForPlace(source)
        if (groupingUpdateScheduler.requestUpdateGrouping(newValue, project) == UpdateStatus.OK) {
          withContext(Dispatchers.EDT) {
            changeSupport.firePropertyChange(PROP_GROUPING_KEYS, it, newValue)

          }
        }
      }
    }
  }


  fun get(groupingKey: @NonNls String): Boolean {
    return groupingStatesHolder.isGroupingEnabled(source, groupingKey)
  }

  fun set(groupingKey: String, state: Boolean) {
    val oldGroupingKeys = groupingStatesHolder.getGroupingsForPlace(source)
    val currentState = oldGroupingKeys.contains(groupingKey)
    if (currentState == state) return

    groupingStatesHolder.setGroupingEnabled(source, groupingKey, state)
    updateFlow.tryEmit(oldGroupingKeys)
  }

  fun isNone(): Boolean = groupingKeys.isEmpty()

  companion object {
    const val PROP_GROUPING_KEYS: String = "ChangesGroupingKeys" // NON-NLS
  }
}
