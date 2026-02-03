// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.ui.icons.IconId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Serializable
@ApiStatus.Internal
class SeTypeVisibilityStatePresentation(val name: @Nls String, val iconId: IconId?, val isEnabled: Boolean) {
  fun cloneWithEnabled(isEnabled: Boolean): SeTypeVisibilityStatePresentation = SeTypeVisibilityStatePresentation(name, iconId, isEnabled)
}