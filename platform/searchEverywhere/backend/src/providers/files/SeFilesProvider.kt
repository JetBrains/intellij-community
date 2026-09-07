// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetRawItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.Nls

internal class SeFilesProvider(private val project: Project,
                               dataContext: DataContext) : SeItemsProvider {
  override val id: String get() = SeProviderIdUtils.FILES_ID
  override val displayName: @Nls String get() = IdeBundle.message("search.everywhere.group.name.files")

  private val provider = SeTargetItemsProvider(project, dataContext, null, "SeFiles") { project, _, hiddenTypes ->
    createModel(project, hiddenTypes)
  }

  override suspend fun collectItems(
    params: SeParams,
    collector: SeItemsProvider.Collector,
  ) = coroutineScope {
    provider.getItemsFlow(params, presentationProvider = { fetchPresentation(it) })
      .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
      .takeWhile {
        collector.put(it)
      }
      .collect()
  }

  private suspend fun fetchPresentation(item: SeTargetRawItem): SeTargetPresentableItem {
    // Fetching mock
  }

  override suspend fun itemSelected(
    item: SeItem,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    TODO("not implemented")
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    TODO("not implemented")
  }

  override fun dispose() {
    TODO("not implemented")
  }

  companion object {
    private fun createModel(project: Project, hiddenTypes: Set<FileTypeRef>): FilteringGotoByModel<FileTypeRef> {
      val model = GotoFileModel(project)
      model.setFilterItems(hiddenTypes)
      return model
    }
  }
}
