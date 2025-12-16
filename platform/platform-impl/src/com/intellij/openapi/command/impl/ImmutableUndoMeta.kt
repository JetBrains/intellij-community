// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@ApiStatus.Internal
class ImmutableUndoMeta(
  private val project: Project?,
  private val editor: FileEditor?,
  private val document: DocumentReference?
): UndoMeta {
  override fun undoProject(): Project? = project
  override fun focusedEditor(): FileEditor? = editor
  override fun focusedDocument(): DocumentReference? = document

  companion object {
    fun create(
      project: Project?,
      editor: FileEditor?,
      document: DocumentReference?,
    ): UndoMeta {
      if (project == null && editor == null && document == null) {
        return EmptyUndoMeta
      }
      return ImmutableUndoMeta(project, editor, document)
    }
  }
}

private object EmptyUndoMeta : UndoMeta {
  override fun undoProject(): Project? = null
  override fun focusedEditor(): FileEditor? = null
  override fun focusedDocument(): DocumentReference? = null
}
