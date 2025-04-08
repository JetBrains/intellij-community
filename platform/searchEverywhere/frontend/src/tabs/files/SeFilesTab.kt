// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.providers.files.SeFilesFilter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@Internal
class SeFilesTab(private val delegate: SeTabDelegate, private val scopeInfo: SeSearchScopesInfo?): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.files")
  override val shortName: String get() = name
  override val id: String get() = "FileSearchEverywhereContributor"

  override fun getItems(params: SeParams): Flow<SeResultEvent> =
    delegate.getItems(params)

  override fun getFilterEditor(): ObservableOptionEditor<SeFilterState>? = scopeInfo?.let { SeFilesFilterEditor(scopeInfo) }

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}

@Internal
class SeFilesFilterEditor(val scopesInfo: SeSearchScopesInfo) : SeFilterEditor<SeFilesFilter>(
  SeFilesFilter(scopesInfo.selectedScopeId)
) {
  private val descriptors: Map<String, ScopeDescriptor> = scopesInfo.scopes.associate {
    val descriptor =
      if (it.isSeparator) ScopeSeparator(it.name)
      else object : ScopeDescriptor(object : GlobalSearchScope() {
        override fun contains(file: VirtualFile): Boolean = throw IllegalStateException("Should not be called")
        override fun isSearchInModuleContent(aModule: Module): Boolean = throw IllegalStateException("Should not be called")
        override fun isSearchInLibraries(): Boolean = throw IllegalStateException("Should not be called")
      }) {
        override fun getColor(): Color? = it.colorId?.color()
        override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Sentence) String = it.name
        override fun getIcon(): Icon? = it.iconId?.icon()
      }

    it.scopeId to descriptor
  }

  override fun getActions(): List<AnAction> = listOf(
    object : ScopeChooserAction() {
      val canToggleEverywhere = scopesInfo.canToggleEverywhere

      override fun onScopeSelected(o: ScopeDescriptor) {
        scopesInfo.scopes.firstOrNull { it.name == o.displayName }?.let {
          SeFilesFilter(it.scopeId)
        }?.let {
          filterValue = it
        }
      }

      override fun getSelectedScope(): ScopeDescriptor = filterValue.selectedScopeId?.let { descriptors[it] }
                                                         ?: ScopeDescriptor(GlobalSearchScope.EMPTY_SCOPE)

      override fun onProjectScopeToggled() {
        isEverywhere = filterValue.selectedScopeId != scopesInfo.everywhereScopeId
      }

      override fun processScopes(processor: Processor<in ScopeDescriptor>): Boolean {
        return descriptors.values.all { processor.process(it) }
      }

      override fun isEverywhere(): Boolean = filterValue.selectedScopeId == scopesInfo.everywhereScopeId

      override fun setEverywhere(everywhere: Boolean) {
        val targetScope = if (everywhere) scopesInfo.everywhereScopeId else scopesInfo.projectScopeId ?: return
        filterValue = SeFilesFilter(targetScope)
      }

      override fun canToggleEverywhere(): Boolean {
        if (!canToggleEverywhere) return false
        return filterValue.selectedScopeId != scopesInfo.everywhereScopeId ||
               filterValue.selectedScopeId != scopesInfo.projectScopeId
      }
    }
  )
}
