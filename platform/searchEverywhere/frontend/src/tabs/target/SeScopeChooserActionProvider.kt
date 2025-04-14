// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.target

import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeSearchScopesInfo
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
class SeScopeChooserActionProvider(val scopesInfo: SeSearchScopesInfo, private val onSelectedScopeChanged: (String?) -> Unit) {
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

  var selectedScopeId: String? = scopesInfo.selectedScopeId
    private set(value) {
      field = value
      onSelectedScopeChanged(value)
    }

  fun getAction(): AnAction {
    return object : ScopeChooserAction() {
      val canToggleEverywhere = scopesInfo.canToggleEverywhere

      override fun onScopeSelected(o: ScopeDescriptor) {
        scopesInfo.scopes.firstOrNull { it.name == o.displayName }?.let {
          selectedScopeId = it.scopeId
        }
      }

      override fun getSelectedScope(): ScopeDescriptor = selectedScopeId?.let { descriptors[it] }
                                                         ?: ScopeDescriptor(GlobalSearchScope.EMPTY_SCOPE)

      override fun onProjectScopeToggled() {
        isEverywhere = selectedScopeId != scopesInfo.everywhereScopeId
      }

      override fun processScopes(processor: Processor<in ScopeDescriptor>): Boolean {
        return descriptors.values.all { processor.process(it) }
      }

      override fun isEverywhere(): Boolean = selectedScopeId == scopesInfo.everywhereScopeId

      override fun setEverywhere(everywhere: Boolean) {
        val targetScope = if (everywhere) scopesInfo.everywhereScopeId else scopesInfo.projectScopeId ?: return
        selectedScopeId = targetScope
      }

      override fun canToggleEverywhere(): Boolean {
        if (!canToggleEverywhere) return false
        return selectedScopeId != scopesInfo.everywhereScopeId ||
               selectedScopeId != scopesInfo.projectScopeId
      }
    }
  }
}