// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorStateWithPreferredOpenMode
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference

/**
 * A place in the [IdeDocumentHistory]: a file, the editor that showed it, and the navigation state of that editor.
 *
 * The history keeps two lists of places.
 * The back places are the places that the user navigated away from.
 * The change places are the places where the user changed a document.
 * [RecentPlacesListener] reports the changes of both lists.
 *
 * Only the platform creates instances. An instance does not change after the platform creates it.
 */
@ApiStatus.Experimental
class PlaceInfo @ApiStatus.Internal constructor(
  /**
   * The file of the place.
   */
  val file: VirtualFile,

  /**
   * The state of the editor at this place, taken at the [FileEditorStateLevel.NAVIGATION] level.
   */
  val navigationState: FileEditorState,

  /**
   * The [FileEditorProvider.getEditorTypeId] of the editor that showed the place.
   */
  val editorTypeId: String,

  window: EditorWindow?,

  /**
   * `true` if the file was open in the preview tab.
   */
  val isPreviewTab: Boolean,

  /**
   * The caret offset at this place, or `null` if the editor is not a text editor.
   *
   * The marker follows the edits of the document. Check [RangeMarker.isValid] before you read it.
   */
  val caretPosition: RangeMarker?,

  /**
   * The time when the history recorded the place, in milliseconds since the epoch, or `-1` if it is not known.
   */
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

  /**
   * The editor window that showed the place.
   *
   * The place holds the window weakly. The value is `null` if the window is not known or the garbage collector removed it.
   * A closed window can stay here, so check [EditorWindow.isDisposed] before you use it.
   */
  val window: EditorWindow?
    get() = windowRef.get()

  @ApiStatus.Internal
  fun getOpenMode(): FileEditorManagerImpl.OpenMode? {
    return (navigationState as? FileEditorStateWithPreferredOpenMode)?.openMode
  }

  override fun toString(): String = file.getName() + " " + navigationState
}
