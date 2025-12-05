// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.presentations

import com.intellij.platform.searchEverywhere.SeExtendedInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SeItemPresentation {
  val text: String
  val extendedInfo: SeExtendedInfo? get() = null
  val isMultiSelectionSupported: Boolean

  @ApiStatus.Internal
  fun contentEquals(other: SeItemPresentation?): Boolean {
    if (other == null) return false
    return text == other.text && extendedInfo == other.extendedInfo
  }
}
