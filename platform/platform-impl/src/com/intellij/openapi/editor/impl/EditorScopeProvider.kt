// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class EditorScopeProvider(private val cs: CoroutineScope) {

  @RequiresEdt
  fun getEditorScope(editor: Editor): CoroutineScope {
    val existingScope = editor.getUserData(EDITOR_SCOPE_KEY)
    if (existingScope != null) return existingScope

    val editorScope = cs.childScope("LogpointLifetimeScopeProvider@${editor.document.hashCode()}")
    EditorUtil.disposeWithEditor(editor) {
      editorScope.cancel()
    }
    editor.putUserData(EDITOR_SCOPE_KEY, editorScope)
    return editorScope
  }

  companion object {
    fun getInstance(project: Project): EditorScopeProvider = project.service()

    private val EDITOR_SCOPE_KEY = Key.create<CoroutineScope>("EDITOR_SCOPE_KEY")
  }
}

