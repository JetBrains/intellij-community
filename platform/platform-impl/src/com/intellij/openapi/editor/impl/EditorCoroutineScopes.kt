// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
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
}
