// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.backend.presentation.TargetPresentation
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
sealed interface SearchEverywhereItemPresentation {
  val text: String
}

@ApiStatus.Internal
data class ActionItemPresentation(
  val icon: Icon? = null,
  override val text: String,
  val location: String? = null,
  val switcherState: Boolean? = null,
  val isEnabled: Boolean = true,
  val shortcut: String? = null,
): SearchEverywhereItemPresentation

@ApiStatus.Internal
class TargetItemPresentation(val targetPresentation: TargetPresentation): SearchEverywhereItemPresentation {
  override val text: String
    get() = targetPresentation.presentableText
}
