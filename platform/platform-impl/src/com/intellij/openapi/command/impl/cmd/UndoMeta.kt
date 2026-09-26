// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project


interface UndoMeta {
  fun undoProject(): Project?
  fun focusedEditor(): FileEditor?
  fun undoableActions(): List<UndoableActionMeta>
  fun isForcedGlobal(): Boolean

  companion object {
    @JvmStatic
    fun create(undoProject: Project?, focusedEditor: FileEditor?): UndoMeta {
      return create(undoProject, focusedEditor, emptyList(), false)
    }

    @JvmStatic
    fun create(
      project: Project?,
      editor: FileEditor?,
      actions: List<UndoableActionMeta>,
      isForcedGlobal: Boolean,
    ): UndoMeta {
      if (project == null && editor == null && actions.isEmpty() && !isForcedGlobal) {
        return EmptyUndoMeta
      }
      return ImmutableUndoMeta(project, editor, actions, isForcedGlobal)
    }
  }

  private class ImmutableUndoMeta(
    private val project: Project?,
    private val editor: FileEditor?,
    private val actions: List<UndoableActionMeta>,
    private val isForcedGlobal: Boolean,
  ) : UndoMeta {
    override fun undoProject(): Project? = project
    override fun focusedEditor(): FileEditor? = editor
    override fun undoableActions(): List<UndoableActionMeta> = actions
    override fun isForcedGlobal(): Boolean = isForcedGlobal
  }

  private object EmptyUndoMeta : UndoMeta {
    override fun undoProject(): Project? = null
    override fun focusedEditor(): FileEditor? = null
    override fun undoableActions(): List<UndoableActionMeta> = emptyList()
    override fun isForcedGlobal(): Boolean = false
  }
}
