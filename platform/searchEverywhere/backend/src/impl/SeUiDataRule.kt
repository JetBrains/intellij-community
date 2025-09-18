// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.data.SeDataKeys
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeUiDataRule: UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val selectedItems = snapshot[SeDataKeys.SPLIT_SE_SELECTED_ITEMS] ?: return
    val items = selectedItems.mapNotNull { it.fetchItemIfExists() }

    val onlyItem = items.firstOrNull().takeIf { items.size == 1 }

    sink[PlatformCoreDataKeys.SELECTED_ITEM] = onlyItem?.rawObject
    sink[PlatformCoreDataKeys.SELECTED_ITEMS] = items.map { it.rawObject }.toTypedArray()

    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) {
      items.mapNotNull {
        getDataFromElementInfo(CommonDataKeys.PSI_ELEMENT.name, it) as? PsiElement
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    sink.lazy(PlatformCoreDataKeys.VIRTUAL_FILE_ARRAY) {
      items.mapNotNull {
        getDataFromElementInfo(CommonDataKeys.VIRTUAL_FILE.name, it) as? VirtualFile
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    sink.lazy(PlatformCoreDataKeys.NAVIGATABLE_ARRAY) {
      items.mapNotNull {
        getDataFromElementInfo(CommonDataKeys.NAVIGATABLE.name, it) as? Navigatable
        ?: getDataFromElementInfo(CommonDataKeys.PSI_ELEMENT.name, it) as? Navigatable
      }.takeIf {
        it.isNotEmpty()
      }?.toTypedArray()
    }

    if (onlyItem != null) {
      sink[PlatformCoreDataKeys.BGT_DATA_PROVIDER] = object: DataProvider {
        override fun getData(dataId: String): Any? {
          return getDataFromElementInfo(dataId, onlyItem)
        }
      }
    }
  }

  private fun getDataFromElementInfo(dataId: String, item: SeItem): Any? {
    if (item !is SeLegacyItem) return null

    @Suppress("UNCHECKED_CAST")
    val contributor = item.contributor as SearchEverywhereContributor<Any>
    return contributor.getDataForItem(item.rawObject, dataId)
  }
}