// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.addDataForItem
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeCommandInfoFactory
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProviderWithPossibleOperationDisposable
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SeWrappedLegacyContributorItemsProvider: SeItemsProviderWithPossibleOperationDisposable {
  abstract val contributor: SearchEverywhereContributor<*>

  override fun addDataForItem(item: SeItem, sink: DataSink) {
    if (item !is SeLegacyItem) return

    @Suppress("UNCHECKED_CAST")
    val contributor = item.contributor as? SearchEverywhereContributor<Any> ?: return
    contributor.addDataForItem(item.rawObject, sink)
  }

  override fun getPsiElementForItem(item: SeItem): PsiElement? =
    getDataFromElementInfo(PlatformCoreDataKeys.PSI_ELEMENT, item)

  override fun getVirtualFileForItem(item: SeItem): VirtualFile? =
    getDataFromElementInfo(PlatformCoreDataKeys.VIRTUAL_FILE, item)

  override fun getNavigatableForItem(item: SeItem): Navigatable? =
    getDataFromElementInfo(PlatformCoreDataKeys.NAVIGATABLE, item)

  protected fun getSupportedCommandsFromContributor(): List<SeCommandInfo> {
    return contributor.supportedCommands.map { commandInfo -> SeCommandInfoFactory().create(commandInfo, id) }
  }

  private fun <T : Any> getDataFromElementInfo(key: DataKey<T>, item: SeItem): T? {
    if (item !is SeLegacyItem) return null

    @Suppress("UNCHECKED_CAST")
    val contributor = item.contributor as? SearchEverywhereContributor<Any> ?: return null
    val ctx = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      contributor.addDataForItem(item.rawObject, sink)
    }
    return ctx.getData(key)
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}