// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.searcheverywhere.SearchEverywherePopupInstance
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.SearchListener
import com.intellij.ide.actions.searcheverywhere.SplitSearchListener
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.providers.SeLog
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future
import javax.swing.text.Document

@ApiStatus.Internal
class SePopupInstance(private val popupVm: SePopupVm,
                      private val popupContentPane: SePopupContentPane,
                      private val combinedListener: SeSearchStatePublisher): SearchEverywherePopupInstance {
  val registerShortcut: (AnAction) -> Unit = { action ->
    val shortcut = ActionUtil.getMnemonicAsShortcut(action)
    if (shortcut != null) {
      action.shortcutSet = shortcut
      action.registerCustomShortcutSet(shortcut, popupContentPane)
    }
  }

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
    TODO("Not yet implemented")
  }

  @ApiStatus.Internal
  @TestOnly
  override fun findElementsForPattern(pattern: String): Future<List<Any>> {
    TODO("Not yet implemented")
  }

  fun saveSearchText() {
    popupVm.saveSearchText()
  }

  private val currentTabSearchEverywhereToggleAction: SearchEverywhereToggleAction?
    get() = popupVm.currentTab.filterEditor.getValueOrNull()?.getHeaderActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction
}