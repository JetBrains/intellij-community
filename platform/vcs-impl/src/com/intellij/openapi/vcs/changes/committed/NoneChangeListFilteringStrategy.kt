// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.event.ChangeListener

private val NONE_FILTER_KEY = CommittedChangesFilterKey("None", CommittedChangesFilterPriority.NONE)

@ApiStatus.Internal
object NoneChangeListFilteringStrategy : ChangeListFilteringStrategy {
  override fun getKey(): CommittedChangesFilterKey = NONE_FILTER_KEY

  override fun getFilterUI(): JComponent? = null

  override fun addChangeListener(listener: ChangeListener) = Unit
  override fun removeChangeListener(listener: ChangeListener) = Unit

  override fun setFilterBase(changeLists: List<CommittedChangeList>) = Unit
  override fun resetFilterBase() = Unit
  override fun appendFilterBase(changeLists: List<CommittedChangeList>) = Unit
  override fun filterChangeLists(changeLists: List<CommittedChangeList>): List<CommittedChangeList> =
    java.util.List.copyOf(changeLists)

  override fun toString(): String = VcsBundle.message("filter.none.name")
}