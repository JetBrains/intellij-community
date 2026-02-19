// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
@Serializable
class SearchScopeData(val scopeId: String, val name: @Nls String, val iconId: IconId?, val colorId: ColorId?, val isSeparator: Boolean, val uiNeeded: Boolean) {
  companion object {
    fun from(descriptor: ScopeDescriptor, scopeId: String): SearchScopeData? {
      val name = descriptor.displayName ?: return null
      return SearchScopeData(scopeId,
                             name,
                             descriptor.icon?.rpcId(),
                             descriptor.color?.rpcId(),
                             descriptor is ScopeSeparator,
                             descriptor.needsUserInputForScope())
    }
  }
}

@ApiStatus.Internal
@Serializable
class SearchScopesInfo(val scopes: List<SearchScopeData>,
                       val selectedScopeId: String?,
                       val projectScopeId: String?,
                       val everywhereScopeId: String?) {
  fun getScopeDescriptors(): Map<String, ScopeDescriptor> {
    return scopes.associate {
      val descriptor =
        if (it.isSeparator) ScopeSeparator(it.name)
        else FrontendScopeDescriptor(it)

      it.scopeId to descriptor
    }
  }
}


private class FrontendScopeDescriptor(private val searchScopeData: SearchScopeData) : ScopeDescriptor(object : GlobalSearchScope() {
  override fun contains(file: VirtualFile): Boolean = throw IllegalStateException("Should not be called")
  override fun isSearchInModuleContent(aModule: Module): Boolean = throw IllegalStateException("Should not be called")
  override fun isSearchInLibraries(): Boolean = throw IllegalStateException("Should not be called")
}) {
  override fun getColor(): Color? = searchScopeData.colorId?.color()
  override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Sentence) String = searchScopeData.name
  override fun getIcon(): Icon? = searchScopeData.iconId?.icon()
  override fun needsUserInputForScope(): Boolean {
    return searchScopeData.uiNeeded
  }
}
