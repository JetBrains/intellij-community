// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogParentFilter
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ParentFilterModel(uiProperties: MainVcsLogUiProperties,
                        private val logProviders: Map<VirtualFile, VcsLogProvider>,
                        private val visibleRootsProvider: () -> Collection<VirtualFile>? = { null }, filters: VcsLogFilterCollection?) :
  FilterModel.SingleFilterModel<VcsLogParentFilter>(VcsLogFilterCollection.PARENT_FILTER, uiProperties, filters) {

  private fun createParentFilter(values: List<String>): VcsLogParentFilter? {
    if (values.size != 2) return null
    return VcsLogFilterObject.fromParentCount(values[0].toIntOrNull(), values[1].toIntOrNull())
  }

  override fun createFilter(values: List<String>): VcsLogParentFilter? {
    return createParentFilter(values)
  }

  override fun getFilterValues(filter: VcsLogParentFilter): List<String> {
    return listOf(filter.minParents.toString(), filter.maxParents.toString())
  }

  val isVisible: Boolean
    get() = isSupported(logProviders.keys)

  val isEnabled: Boolean
    get() = isSupported(visibleRootsProvider() ?: logProviders.keys)

  private fun isSupported(roots: Collection<VirtualFile>) =
    roots.any { VcsLogProperties.SUPPORTS_PARENTS_FILTER.getOrDefault(logProviders[it]) }
}