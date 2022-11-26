// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class FileEditorOpenOptions(
  @JvmField var selectAsCurrent: Boolean = true,
  @JvmField var reuseOpen: Boolean = false,
  @JvmField var usePreviewTab: Boolean = false,
  @JvmField var requestFocus: Boolean = false,
  @JvmField var pin: Boolean? = null,
  @JvmField var index: Int = -1,
  @JvmField val isExactState: Boolean = false,
  @JvmField var isReopeningOnStartup: Boolean = false,
) {
  fun clone() = copy()  // no arg copying for Java

  // @formatter:off
  @JvmOverloads fun withSelectAsCurrent(value: Boolean = true)     = apply { selectAsCurrent = value }
  @JvmOverloads fun withReuseOpen(value: Boolean = true)           = apply { reuseOpen = value }
  @JvmOverloads fun withUsePreviewTab(value: Boolean = true)       = apply { usePreviewTab = value }
  @JvmOverloads fun withRequestFocus(value: Boolean = true)        = apply { requestFocus = value }
  fun withPin(value: Boolean?)  = apply { pin = value }
  fun withIndex(value: Int)     = apply { index = value }
  // @formatter:on
}