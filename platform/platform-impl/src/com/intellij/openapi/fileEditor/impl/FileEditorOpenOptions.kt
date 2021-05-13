// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class FileEditorOpenOptions(
  var isCurrentTab: Boolean = false,
  var isFocusEditor: Boolean = false,
  var pin: Boolean? = null,
  var index: Int = -1,
  var isExactState: Boolean = false,
  var isReopeningEditorsOnStartup: Boolean = false,
) {
  // @formatter:off
  fun withCurrentTab(value: Boolean)    = apply { isCurrentTab = value }
  fun withFocusEditor(value: Boolean)   = apply { isFocusEditor = value }
  fun withPin(value: Boolean?)          = apply { pin = value }
  fun withIndex(value: Int)             = apply { index = value }

  @JvmOverloads fun withExactState(value: Boolean = true)                  = apply { isExactState = value }
  @JvmOverloads fun withReopeningEditorsOnStartup(value: Boolean = true)   = apply { isReopeningEditorsOnStartup = value }
  // @formatter:on
}