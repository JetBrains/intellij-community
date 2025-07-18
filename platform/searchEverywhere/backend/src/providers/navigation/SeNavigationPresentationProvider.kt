// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.navigation

import com.intellij.ide.ui.icons.rpcId
import com.intellij.navigation.NavigationItem
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeNavigationItem(val item: NavigationItem, private val weight: Int, val extendedDescription: String?, val isMultiSelectionSupported: Boolean) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeNavigationPresentationProvider().getPresentation(item, extendedDescription, isMultiSelectionSupported)
}

@Internal
class SeNavigationPresentationProvider {
  fun getPresentation(item: NavigationItem, extendedDescription: String?, isMultiSelectionSupported: Boolean): SeItemPresentation {
    return SeTargetItemPresentation(iconId = item.presentation?.getIcon(false)?.rpcId(),
                                    presentableText = item.presentation?.presentableText ?: "",
                                    containerText = item.presentation?.locationString,
                                    extendedDescription = extendedDescription,
                                    isMultiSelectionSupported = isMultiSelectionSupported)
  }
}