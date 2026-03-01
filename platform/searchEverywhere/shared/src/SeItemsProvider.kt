// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeItemsProvider.Collector
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Results provider for Search Everywhere search queries.
 * It's an analog of [com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor]
 *
 * see [SeItemsProviderFactory]
 */
@ApiStatus.Experimental
interface SeItemsProvider : Disposable {
  /**
   * A unique identifier of the provider.
   */
  val id: String

  /**
   * Name of the provider used in the type filter of the All tab
   */
  val displayName: @Nls String

  fun interface Collector {
    suspend fun put(item: SeItem): Boolean
  }

  /**
   * The main method for providing search results.
   * The collector has suspending method put which is used for back pressure: if the UI doesn't need more items, the collector.put() will suspend.
   *
   * @param params Defines the search parameters, including the query and filter state, to guide the collection process.
   * @param collector A callback interface used to collect and manage the retrieved items during the search.
   */
  suspend fun collectItems(params: SeParams, collector: Collector)

  /**
   * Handles the selection of a search item.
   *
   * @param item The search item selected by the user.
   * @param modifiers The modifier keys (e.g., Shift, Ctrl, Alt) that were pressed during the selection.
   * @param searchText The input query that was used to retrieve the item.
   * @return true if the search popup should be closed, false otherwise.
   */
  suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean

  /**
   * Determines whether an item can be displayed in find results.
   *
   * @return true if the item is eligible to be shown in find results, false otherwise.
   */
  suspend fun canBeShownInFindResults(): Boolean

  /**
   * Executes an extended action on the given search item.
   *
   * @param item The search item on which the extended action will be performed.
   * @return true if the search popup should be closed, false otherwise.
   */
  suspend fun performExtendedAction(item: SeItem): Boolean {
    return false
  }

  // Data snapshot methods
  fun addDataForItem(item: SeItem, sink: DataSink) {}
  fun getPsiElementForItem(item: SeItem): PsiElement? = null
  fun getVirtualFileForItem(item: SeItem): VirtualFile? = null
  fun getNavigatableForItem(item: SeItem): Navigatable? = null
}

@ApiStatus.Internal
@ApiStatus.Experimental
interface SeItemsProviderWithPossibleOperationDisposable : SeItemsProvider {
  /**
   * See the comment for [com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider.getRawItemsWithOperationLifetime]
   */
  @ApiStatus.Internal
  suspend fun collectItemsWithOperationLifetime(params: SeParams, operationDisposable: Disposable, collector: Collector) {
    collectItems(params, collector)
  }
}
