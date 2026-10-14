// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorStateWithPreferredOpenMode
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference

@ApiStatus.Internal
class PlaceInfo @ApiStatus.Internal constructor(
  val file: VirtualFile,
  val navigationState: FileEditorState,
  val editorTypeId: String,
  window: EditorWindow?,
  val isPreviewTab: Boolean,
  val caretPosition: RangeMarker?,
  val timeStamp: Long,
) {
  private val windowRef = WeakReference(window)

  @ApiStatus.Internal
  constructor(
    file: VirtualFile,
    navigationState: FileEditorState,
    editorTypeId: String,
    window: EditorWindow?,
    caretPosition: RangeMarker?,
  ) : this(
    file = file,
    navigationState = navigationState,
    editorTypeId = editorTypeId,
    window = window,
    isPreviewTab = false,
    caretPosition = caretPosition,
    timeStamp = -1,
  )

  val window: EditorWindow?
    get() = windowRef.get()

  @ApiStatus.Internal
  fun getOpenMode(): FileEditorManagerImpl.OpenMode? {
    return (navigationState as? FileEditorStateWithPreferredOpenMode)?.openMode
  }

  override fun toString(): String = file.getName() + " " + navigationState
}
