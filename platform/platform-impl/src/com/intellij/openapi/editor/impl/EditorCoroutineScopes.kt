// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.EditorCoroutineScopeService
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

internal object EditorCoroutineScopes {
  /**
   * A scope for one editor, cancelled when [disposable] is disposed.
   *
   * The scope is also a child of the container that owns the editor, so that it cannot outlive that container when
   * [disposable] is never disposed. Cancellation does not wait for the running coroutines, so a component that reads
   * editor state from a coroutine still needs its own disposal guard.
   */
  @JvmStatic
  fun createEditorScope(project: Project?, disposable: Disposable): CoroutineScope {
    val containerScope = EditorCoroutineScopeService.defaultScope(project)
    val editorScope = containerScope.childScope("Editor")
    editorScope.coroutineContext.job.cancelOnDispose(disposable)
    return editorScope
  }

  /**
   * A scope for a background computation that an editor setting needs, such as a per-file read action.
   *
   * An editor owns a scope already, so its settings compute in it, and the computation then stops when the editor is
   * released. [project] serves the settings object that belongs to no editor, which has nothing narrower to use.
   *
   * [project] takes no part when [editor] is present, because the editor is what the result is reported to, and the
   * editor scope already lives under the container that owns the editor. The two differ only for a project-less editor
   * that a caller asks about a project: such a computation runs on the application scope, and only the release of the
   * editor cancels it.
   */
  fun settingsScope(editor: EditorImpl?, project: Project?): CoroutineScope {
    if (editor != null) {
      return editor.coroutineScope
    }
    val container = project ?: ApplicationManager.getApplication()
    return (container as ComponentManagerEx).getCoroutineScope()
  }
}
