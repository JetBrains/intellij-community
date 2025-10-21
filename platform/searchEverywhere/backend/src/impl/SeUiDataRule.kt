// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.data.SeDataKeys
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeUiDataRule: UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val selectedItems = snapshot[SeDataKeys.SPLIT_SE_SELECTED_ITEMS] ?: return
    val isAllTab = snapshot[SeDataKeys.SPLIT_SE_IS_ALL_TAB] ?: return
    val session = snapshot[SeDataKeys.SPLIT_SE_SESSION] ?: return
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val backendService = SeBackendService.getInstance(project)

    val items = selectedItems.mapNotNull {
      val item = it.fetchItemIfExists() ?: return@mapNotNull null
      val provider = backendService.tryGetProvider(it.providerId, isAllTab, session) ?: return@mapNotNull null
      SeItemWithProvider(item, provider)
    }

    val onlyItem = items.firstOrNull().takeIf { items.size == 1 }

    sink[PlatformCoreDataKeys.SELECTED_ITEM] = onlyItem?.item?.rawObject
    sink[PlatformCoreDataKeys.SELECTED_ITEMS] = items.map { it.item.rawObject }.toTypedArray()

    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) {
      items.mapNotNull {
        it.provider.getPsiElementForItem(it.item)
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    sink.lazy(PlatformCoreDataKeys.VIRTUAL_FILE_ARRAY) {
      items.mapNotNull {
        it.provider.getVirtualFileForItem(it.item)
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    sink.lazy(PlatformCoreDataKeys.NAVIGATABLE_ARRAY) {
      items.mapNotNull {
        it.provider.getNavigatableForItem(it.item)
        ?: it.provider.getPsiElementForItem(it.item) as? Navigatable
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    onlyItem?.let {
      it.provider.addDataForItem(it.item, sink)
    }
  }
}

private class SeItemWithProvider(val item: SeItem, val provider: SeItemsProvider)
