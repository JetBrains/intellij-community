// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.presentation.TargetPresentation
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@Serializable
sealed interface SeItemPresentation {
  val text: String
}

@ApiStatus.Internal
@Serializable
class SeTextItemPresentation(override val text: String): SeItemPresentation

@ApiStatus.Internal
sealed interface SeActionItemPresentation: SeItemPresentation {
  val commonData: Common

  @ApiStatus.Internal
  @Serializable
  data class Common(
    val text: String,
    val switcherState: Boolean? = null,
    val location: @Nls String? = null
  )
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
): SeActionItemPresentation {
  override val text: String get() = commonData.text

  @ApiStatus.Internal
  @Serializable
  data class Promo(val productIconId: IconId?,
                   val callToActionText: @Nls String)
}

@ApiStatus.Internal
@Serializable
data class SeOptionActionItemPresentation(
  override val commonData: SeActionItemPresentation.Common,
  val value: @NlsSafe String? = null,
  val isBooleanOption: Boolean = false,
): SeActionItemPresentation {
  override val text: String get() = commonData.text
}

@ApiStatus.Internal
@Serializable
class SeTargetItemPresentation(val targetPresentation: TargetPresentation): SeItemPresentation {
  override val text: String = targetPresentation.presentableText
}
