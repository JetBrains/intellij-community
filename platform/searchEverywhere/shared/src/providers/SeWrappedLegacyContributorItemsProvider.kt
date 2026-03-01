// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeCommandInfoFactory
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderWithPossibleOperationDisposable
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SeWrappedLegacyContributorItemsProvider: SeItemsProviderWithPossibleOperationDisposable {
  abstract val contributor: SearchEverywhereContributor<*>

  override fun addDataForItem(item: SeItem, sink: DataSink) {
    sink[PlatformCoreDataKeys.BGT_DATA_PROVIDER] = DataProvider { dataId -> getDataFromElementInfo(dataId, item) }
  }

  override fun getPsiElementForItem(item: SeItem): PsiElement? =
    getDataFromElementInfo(PlatformCoreDataKeys.PSI_ELEMENT.name, item) as? PsiElement

  override fun getVirtualFileForItem(item: SeItem): VirtualFile? =
    getDataFromElementInfo(PlatformCoreDataKeys.VIRTUAL_FILE.name, item) as? VirtualFile

  override fun getNavigatableForItem(item: SeItem): Navigatable? =
    getDataFromElementInfo(PlatformCoreDataKeys.NAVIGATABLE.name, item) as? Navigatable

  protected fun getSupportedCommandsFromContributor(): List<SeCommandInfo> {
    return contributor.supportedCommands.map { commandInfo -> SeCommandInfoFactory().create(commandInfo, id) }
  }

  private fun getDataFromElementInfo(dataId: String, item: SeItem): Any? {
    if (item !is SeLegacyItem) return null

    @Suppress("UNCHECKED_CAST")
    val contributor = item.contributor as? SearchEverywhereContributor<Any>
    return contributor?.getDataForItem(item.rawObject, dataId)
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}