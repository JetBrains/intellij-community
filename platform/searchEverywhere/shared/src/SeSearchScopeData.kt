// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Serializable
class SeSearchScopeData(val scopeId: String, val name: @Nls String, val iconId: IconId?, val colorId: ColorId?, val isSeparator: Boolean) {
  companion object {
    fun from(descriptor: ScopeDescriptor, scopeId: String): SeSearchScopeData? {
      val name = descriptor.displayName ?: return null
      return SeSearchScopeData(scopeId,
                               name,
                               descriptor.icon?.rpcId(),
                               descriptor.color?.rpcId(),
                               descriptor is ScopeSeparator)
    }
  }
}

@ApiStatus.Internal
@Serializable
class SeSearchScopesInfo(val scopes: List<SeSearchScopeData>,
                         val selectedScopeId: String?,
                         val canToggleEverywhere: Boolean,
                         val projectScopeId: String?,
                         val everywhereScopeId: String?)
