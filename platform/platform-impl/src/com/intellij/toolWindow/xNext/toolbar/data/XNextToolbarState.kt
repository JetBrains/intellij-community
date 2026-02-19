// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.data

internal data class XNextToolbarState(
  val recent: List<String>,
  val pinned: List<String>,
) {
  companion object {
    val EMPTY:XNextToolbarState = XNextToolbarState(emptyList(), emptyList())
  }
}