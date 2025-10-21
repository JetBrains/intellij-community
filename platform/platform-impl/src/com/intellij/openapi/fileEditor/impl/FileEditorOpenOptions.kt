// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
data class FileEditorOpenOptions(
  @JvmField val selectAsCurrent: Boolean = true,
  @JvmField val reuseOpen: Boolean = false,
  @JvmField val usePreviewTab: Boolean = false,
  @JvmField val requestFocus: Boolean = false,
  @JvmField val pin: Boolean = false,
  @JvmField val index: Int = -1,
  @JvmField val isExactState: Boolean = false,
  @Internal @JvmField val openMode: FileEditorManagerImpl.OpenMode? = null,
  @Internal @JvmField val waitForCompositeOpen: Boolean = true,
  // it makes sense only if openMode == NEW_WINDOW
  @Internal @JvmField val isSingletonEditorInWindow: Boolean = false,
  @Internal @JvmField val forceFocus: Boolean = false,
  /**
    * **DO NOT USE!**
    *
    * IJPL-183875: Workaround to open an explicitly set composite that has been supplied from the backend.
    * Closure is used instead of `EditorComposite?`, since if the composite is created, it will start initialization.
    * However, frontend requires showing the composite asap
   **/
  @Internal @JvmField val explicitlyOpenCompositeProvider: (() -> EditorComposite?)? = null,
) {
  @Contract(pure = true)
  // no arg copying for Java
  fun clone(): FileEditorOpenOptions = copy()

  @Suppress("unused")
  @Contract(pure = true)
  @JvmOverloads
  fun withSelectAsCurrent(value: Boolean = true): FileEditorOpenOptions = copy(selectAsCurrent = value)

  @Contract(pure = true)
  @JvmOverloads
  fun withReuseOpen(value: Boolean = true): FileEditorOpenOptions = copy(reuseOpen = value)

  @Contract(pure = true)
  @JvmOverloads
  fun withUsePreviewTab(value: Boolean = true): FileEditorOpenOptions = copy(usePreviewTab = value)

  @Contract(pure = true)
  @JvmOverloads
  fun withRequestFocus(value: Boolean = true): FileEditorOpenOptions = copy(requestFocus = value)

  @Experimental
  @Contract(pure = true)
  fun withOpenMode(openMode: FileEditorManagerImpl.OpenMode?): FileEditorOpenOptions = copy(openMode = openMode)
}