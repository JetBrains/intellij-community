// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.searchComponents.ExtendableSearchTextField
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTextField : ExtendableSearchTextField() {
  private val mySearchProcessExtension =
    ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }

  init {
    isOpaque = true
  }

  fun setSearchInProgress(inProgress: Boolean) {
    removeExtension(mySearchProcessExtension)
    if (inProgress) addExtension(mySearchProcessExtension)
  }
}