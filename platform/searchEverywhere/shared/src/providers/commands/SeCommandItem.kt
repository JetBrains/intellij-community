// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.commands

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.icons.rpcId
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeCommandItem(
  val commandInfo: SeCommandInfo,
) : SeItem {
  override fun weight(): Int = 10 // same as in SearchEverywhereUI.myStubCommandContributor
  override suspend fun presentation(): SeItemPresentation {
    return SeTargetItemPresentation(
      iconId = EmptyIcon.ICON_16.rpcId(),
      presentableText = SearchTopHitProvider.getTopHitAccelerator() + commandInfo.command,
      containerText = commandInfo.definition,
      extendedInfo = null,
      isMultiSelectionSupported = false)
  }

  override val rawObject: Any get() = commandInfo
}
