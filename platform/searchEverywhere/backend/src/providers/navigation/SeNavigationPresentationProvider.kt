// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.navigation

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.ui.icons.rpcId
import com.intellij.navigation.NavigationItem
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeNavigationItem(
  override val rawObject: NavigationItem,
  override val contributor: SearchEverywhereContributor<*>,
  private val weight: Int,
  val extendedInfo: SeExtendedInfo?,
  val isMultiSelectionSupported: Boolean
) : SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeNavigationPresentationProvider().getPresentation(rawObject, extendedInfo, isMultiSelectionSupported)
}

@Internal
class SeNavigationPresentationProvider {
  fun getPresentation(item: NavigationItem, extendedInfo: SeExtendedInfo?, isMultiSelectionSupported: Boolean): SeItemPresentation {
    return SeTargetItemPresentation(iconId = item.presentation?.getIcon(false)?.rpcId(),
                                    presentableText = item.presentation?.presentableText ?: "",
                                    containerText = item.presentation?.locationString,
                                    extendedInfo = extendedInfo,
                                    isMultiSelectionSupported = isMultiSelectionSupported)
  }
}