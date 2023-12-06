// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
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
  @Experimental @JvmField val openMode: FileEditorManagerImpl.OpenMode? = null,
) {
  @Contract(pure = true)
  fun clone(): FileEditorOpenOptions = copy()  // no arg copying for Java

  // @formatter:off
  @Contract(pure = true) @JvmOverloads fun withSelectAsCurrent(value: Boolean = true):FileEditorOpenOptions     = copy(selectAsCurrent = value)
  @Contract(pure = true) @JvmOverloads fun withReuseOpen(value: Boolean = true):FileEditorOpenOptions           = copy(reuseOpen = value)
  @Contract(pure = true) @JvmOverloads fun withUsePreviewTab(value: Boolean = true):FileEditorOpenOptions       = copy(usePreviewTab = value)
  @Contract(pure = true) @JvmOverloads fun withRequestFocus(value: Boolean = true):FileEditorOpenOptions        = copy(requestFocus = value)
  @Contract(pure = true)               fun withPin(value: Boolean = true):FileEditorOpenOptions                 = copy(pin = value)
  @Contract(pure = true)               fun withIndex(value: Int):FileEditorOpenOptions                          = copy(index = value)
  // @formatter:on
}