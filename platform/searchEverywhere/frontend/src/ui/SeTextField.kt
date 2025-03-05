// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ui.searchComponents.ExtendableSearchTextField
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTextField : ExtendableSearchTextField() {
  init {
    isOpaque = true
  }
}