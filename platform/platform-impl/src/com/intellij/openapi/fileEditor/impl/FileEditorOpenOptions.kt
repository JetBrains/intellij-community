// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@Suppress("unused")
@ApiStatus.Internal
data class FileEditorOpenOptions(
  @JvmField val selectAsCurrent: Boolean = true,
  @JvmField val reuseOpen: Boolean = false,
  @JvmField val usePreviewTab: Boolean = false,
  @JvmField val requestFocus: Boolean = false,
  @JvmField val pin: Boolean = false,
  @JvmField val index: Int = -1,
  @JvmField val isExactState: Boolean = false,
) {
  @Contract(pure = true)
  fun clone() = copy()  // no arg copying for Java

  // @formatter:off
  @Contract(pure = true) @JvmOverloads fun withSelectAsCurrent(value: Boolean = true)     = copy(selectAsCurrent = value)
  @Contract(pure = true) @JvmOverloads fun withReuseOpen(value: Boolean = true)           = copy(reuseOpen = value)
  @Contract(pure = true) @JvmOverloads fun withUsePreviewTab(value: Boolean = true)       = copy(usePreviewTab = value)
  @Contract(pure = true) @JvmOverloads fun withRequestFocus(value: Boolean = true)        = copy(requestFocus = value)
  @Contract(pure = true)               fun withPin(value: Boolean = true)                 = copy(pin = value)
  @Contract(pure = true)               fun withIndex(value: Int)                          = copy(index = value)
  // @formatter:on
}