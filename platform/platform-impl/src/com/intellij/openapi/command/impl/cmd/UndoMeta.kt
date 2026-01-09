// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project


interface UndoMeta {
  fun undoProject(): Project?
  fun focusedEditor(): FileEditor?
  fun focusedDocument(): DocumentReference?

  companion object {
    @JvmStatic
    fun create(project: Project?, editor: FileEditor?, docRef: DocumentReference?): UndoMeta {
      if (project == null && editor == null && docRef == null) {
        return EmptyUndoMeta
      }
      return ImmutableUndoMeta(project, editor, docRef)
    }
  }
}

private class ImmutableUndoMeta(
  private val project: Project?,
  private val editor: FileEditor?,
  private val docRef: DocumentReference?
): UndoMeta {
  override fun undoProject(): Project? = project
  override fun focusedEditor(): FileEditor? = editor
  override fun focusedDocument(): DocumentReference? = docRef
}

private object EmptyUndoMeta : UndoMeta {
  override fun undoProject(): Project? = null
  override fun focusedEditor(): FileEditor? = null
  override fun focusedDocument(): DocumentReference? = null
}
