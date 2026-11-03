// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.searcheverywhere.SearchEverywherePopupInstance
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.SearchListener
import com.intellij.ide.actions.searcheverywhere.SplitSearchListener
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeSearchRestartListener
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.providers.SeLog
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.text.Document

@ApiStatus.Internal
class SePopupInstance(private val project: Project?,
                      private val popupVm: SePopupVm,
                      private val popupContentPane: SePopupContentPane,
                      private val combinedListener: SeSearchStatePublisher): SearchEverywherePopupInstance {
  val registerShortcut: (AnAction) -> Unit = { action -> popupContentPane.registerMnemonicShortcut(action) }

  override fun getSearchText(): String = popupVm.searchPattern.value

  override fun setSearchText(searchText: String?) {
    popupVm.setSearchText(searchText ?: "")
  }

  override fun getSearchFieldDocument(): Document = popupContentPane.searchFieldDocument

  override fun closePopup() {
    popupVm.closePopup()
  }

  override fun addSearchListener(listener: SearchListener) {
    SeLog.warn("SearchListener is not supported in the split implementation. Please, use addSplitSearchListener instead.")
  }

  override fun addSplitSearchListener(listener: SplitSearchListener) {
    combinedListener.addListener(listener)
  }

  fun getSelectedTabID(): String = popupVm.currentTab.tabId

  fun setSelectedTabID(tabID: String) {
    popupVm.showTab(tabID)
  }

  fun toggleEverywhereFilter() {
    val action = currentTabSearchEverywhereToggleAction ?: return
    if (action.canToggleEverywhere()) {
      action.isEverywhere = !action.isEverywhere
    }
  }

  fun isEverywhere(): Boolean = currentTabSearchEverywhereToggleAction?.isEverywhere ?: true

  @ApiStatus.Internal
  override fun selectFirstItem() {
    popupContentPane.selectFirstItem()
  }

  @ApiStatus.Internal
  override fun changeScope(processor: (ScopeDescriptor, List<ScopeDescriptor>) -> ScopeDescriptor?) {
    SeLog.warn("Changing scope is not supported in the split implementation.")
  }

  /**
   * Searches for [pattern] in the selected tab, and returns the raw objects of the result list after the search.
   *
   * Only the finished search of [pattern] completes the future.
   * An earlier search, a paused search, or a search that the next search stops does not complete it.
   * When the search field already holds [pattern], the search runs again.
   */
  @ApiStatus.Internal
  @TestOnly
  override fun findElementsForPattern(pattern: String): Future<List<Any>> {
    val future = CompletableFuture<List<Any>>()

    ApplicationManager.getApplication().invokeLater {
      val searchedPattern = pattern
      val listener = object : SplitSearchListener {
        private var isSearchOfPattern = false

        override fun searchStarted(pattern: String, tabId: String) {
          isSearchOfPattern = pattern == searchedPattern
        }

        override fun elementsAdded(uuidToElement: Map<String, Any>) {}

        override fun searchFinished(count: Int) {
          // A negative count means that the next search stopped this one. A paused search has no finished results yet.
          if (!isSearchOfPattern || count < 0) return
          val finishedResults = popupContentPane.finishedSearchResults ?: return

          combinedListener.removeListener(this)
          future.complete(finishedResults.mapNotNull { it.fetchItemIfExists()?.rawObject })
        }
      }
      combinedListener.addListener(listener)

      if (popupVm.searchPattern.value == pattern) {
        project?.messageBus?.syncPublisher(SeSearchRestartListener.TOPIC)?.restartSearch()
      }
      else {
        popupContentPane.searchFieldDocument.remove(0, popupContentPane.searchFieldDocument.length)
        popupContentPane.searchFieldDocument.insertString(0, pattern, null)
      }
    }

    return future
  }

  /** Makes the tabs load all their results, as if the user scrolls to the end of the list. */
  @ApiStatus.Internal
  @TestOnly
  fun loadAllResults() {
    popupContentPane.loadAllResults()
  }

  fun saveSearchText() {
    popupVm.saveSearchText()
  }

  private val currentTabSearchEverywhereToggleAction: SearchEverywhereToggleAction?
    get() = popupVm.currentTab.filterEditor.getValueOrNull()?.getHeaderActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction
}