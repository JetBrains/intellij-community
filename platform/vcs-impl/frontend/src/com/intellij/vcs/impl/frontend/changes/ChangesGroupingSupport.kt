// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTreeGroupingUpdateScheduler
import com.intellij.vcs.impl.shared.rpc.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

@ApiStatus.Internal
open class ChangesGroupingSupport(val project: Project, private val source: String, private val cs: CoroutineScope) {
  private val changeSupport = PropertyChangeSupport(source)
  private val groupingUpdateScheduler = ShelfTreeGroupingUpdateScheduler.getInstance(project)
  private val groupingStatesHolder = ChangesGroupingStatesHolder.getInstance(project)
  private val groupingKeys get() = groupingStatesHolder.getGroupingsForPlace(source)


  fun get(groupingKey: @NonNls String): Boolean {
    return groupingStatesHolder.isGroupingEnabled(source, groupingKey)
  }

  fun set(groupingKey: String, state: Boolean) {
    val oldGroupingKeys = groupingStatesHolder.getGroupingsForPlace(source)
    val currentState = oldGroupingKeys.contains(groupingKey)
    if (currentState == state) return

    groupingStatesHolder.setGroupingEnabled(source, groupingKey, state)
    cs.launch(Dispatchers.IO) {
      val newValue = groupingStatesHolder.getGroupingsForPlace(source)
      if (groupingUpdateScheduler.requestUpdateGrouping(newValue, project) == UpdateStatus.OK) {
        withContext(Dispatchers.EDT) {
          changeSupport.firePropertyChange(PROP_GROUPING_KEYS, oldGroupingKeys, newValue)
        }
      }
    }
  }

  fun isNone() = groupingKeys.isEmpty()

  companion object {
    const val PROP_GROUPING_KEYS = "ChangesGroupingKeys" // NON-NLS
  }
}