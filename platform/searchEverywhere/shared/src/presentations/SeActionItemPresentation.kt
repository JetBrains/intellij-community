// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
sealed interface SeActionItemPresentation : SeItemPresentation {
  val commonData: Common

  @ApiStatus.Internal
  @Serializable
  data class Common(
    val text: @Nls String,
    val location: @Nls String? = null,
    private var _switcherState: Boolean? = null,
    val extendedInfo: SeExtendedInfo? = null,
  ) {
    val switcherState: Boolean? get() = _switcherState
    fun toggleStateIfSwitcher() {
      _switcherState = _switcherState?.not()
    }
  }

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeActionItemPresentation) return false

    return super.contentEquals(other) && commonData == other.commonData
  }
}

@ApiStatus.Internal
@Serializable
data class SeRunnableActionItemPresentation(
  override val commonData: SeActionItemPresentation.Common,
  val toolTip: @Nls String? = null,
  val actionId: @Nls String? = null,
  val isEnabled: Boolean = true,
  val shortcut: @NlsSafe String? = null,
  val promo: Promo? = null,
  val iconId: IconId? = null,
  val selectedIconId: IconId? = null,
  override val isMultiSelectionSupported: Boolean,
) : SeActionItemPresentation {
  override val text: String get() = commonData.text
  override val extendedInfo: SeExtendedInfo? get() = commonData.extendedInfo

  @ApiStatus.Internal
  @Serializable
  data class Promo(
    val productIconId: IconId?,
    val callToActionText: @Nls String,
  )

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeRunnableActionItemPresentation) return false

    return super.contentEquals(other) &&
           toolTip == other.toolTip &&
           actionId == other.actionId &&
           shortcut == other.shortcut &&
           promo == other.promo
  }
}

@ApiStatus.Internal
@Serializable
data class SeOptionActionItemPresentation(
  override val commonData: SeActionItemPresentation.Common,
  val value: @NlsSafe String? = null,
  val isBooleanOption: Boolean = false,
  override val isMultiSelectionSupported: Boolean,
) : SeActionItemPresentation {
  override val text: String get() = commonData.text
  override val extendedInfo: SeExtendedInfo? get() = commonData.extendedInfo

  override fun contentEquals(other: SeItemPresentation?): Boolean {
    if (this === other) return true
    if (other !is SeOptionActionItemPresentation) return false

    return super.contentEquals(other) &&
           commonData == other.commonData &&
           value == other.value &&
           isBooleanOption == other.isBooleanOption
  }
}
