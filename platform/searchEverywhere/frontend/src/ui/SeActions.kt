// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
interface SeActions {
  companion object {
    const val SELECT_ITEM: @NonNls String = "SearchEverywhere.SelectItem"
  }
}