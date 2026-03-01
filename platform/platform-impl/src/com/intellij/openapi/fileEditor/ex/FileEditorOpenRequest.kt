// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Simplified API options for external use.
 *
 * @see com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
 */
@ApiStatus.Experimental
class FileEditorOpenRequest @JvmOverloads constructor(
  val targetWindow: EditorWindow? = null,
  val openMode: FileEditorOpenMode? = null,
  val selectAsCurrent: Boolean = true,
  val reuseOpen: Boolean = false,
  val usePreviewTab: Boolean = false,
  val requestFocus: Boolean = false,
  val pin: Boolean = false,
) {

  private fun copy(
    targetWindow: EditorWindow? = this.targetWindow,
    openMode: FileEditorOpenMode? = this.openMode,
    selectAsCurrent: Boolean = this.selectAsCurrent,
    reuseOpen: Boolean = this.reuseOpen,
    usePreviewTab: Boolean = this.usePreviewTab,
    requestFocus: Boolean = this.requestFocus,
    pin: Boolean = this.pin,
  ): FileEditorOpenRequest {
    return FileEditorOpenRequest(
      targetWindow = targetWindow,
      openMode = openMode,
      selectAsCurrent = selectAsCurrent,
      reuseOpen = reuseOpen,
      usePreviewTab = usePreviewTab,
      requestFocus = requestFocus,
      pin = pin,
    )
  }

  @Contract(pure = true)
  fun withSelectAsCurrent(value: Boolean): FileEditorOpenRequest = copy(selectAsCurrent = value)

  @Contract(pure = true)
  fun withReuseOpen(value: Boolean): FileEditorOpenRequest = copy(reuseOpen = value)

  @Contract(pure = true)
  fun withUsePreviewTab(value: Boolean): FileEditorOpenRequest = copy(usePreviewTab = value)

  @Contract(pure = true)
  fun withRequestFocus(value: Boolean): FileEditorOpenRequest = copy(requestFocus = value)

  @Contract(pure = true)
  fun withPin(value: Boolean): FileEditorOpenRequest = copy(pin = value)

  @Contract(pure = true)
  fun withOpenMode(openMode: FileEditorOpenMode?): FileEditorOpenRequest = copy(openMode = openMode)

  @Contract(pure = true)
  fun withTargetWindow(targetWindow: EditorWindow?): FileEditorOpenRequest = copy(targetWindow = targetWindow)

  override fun toString(): String {
    return "FileEditorOpenRequest(" +
           "targetWindow=$targetWindow, " +
           "openMode=$openMode, " +
           "selectAsCurrent=$selectAsCurrent, " +
           "reuseOpen=$reuseOpen, " +
           "usePreviewTab=$usePreviewTab, " +
           "requestFocus=$requestFocus, " +
           "pin=$pin)"
  }
}

internal fun buildFileEditorOpenOptions(request: FileEditorOpenRequest): FileEditorOpenOptions {
  return FileEditorOpenOptions(
    selectAsCurrent = request.selectAsCurrent,
    reuseOpen = request.reuseOpen,
    usePreviewTab = request.usePreviewTab,
    requestFocus = request.requestFocus,
    openMode = request.openMode?.let(::mapOpenMode),
  )
}

internal fun mapOpenMode(openMode: FileEditorOpenMode): FileEditorManagerImpl.OpenMode {
  return when (openMode) {
    FileEditorOpenMode.DEFAULT -> FileEditorManagerImpl.OpenMode.DEFAULT
    FileEditorOpenMode.NEW_WINDOW -> FileEditorManagerImpl.OpenMode.NEW_WINDOW
    FileEditorOpenMode.RIGHT_SPLIT -> FileEditorManagerImpl.OpenMode.RIGHT_SPLIT
  }
}

@ApiStatus.Experimental
enum class FileEditorOpenMode {
  NEW_WINDOW, RIGHT_SPLIT, DEFAULT
}