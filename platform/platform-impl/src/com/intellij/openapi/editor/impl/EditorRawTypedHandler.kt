// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx


internal class EditorRawTypedHandler(private val defaultHandler: TypedActionHandler) : TypedActionHandlerEx {

  override fun beforeExecute(editor: Editor, c: Char, context: DataContext, plan: ActionPlan) {
    if (defaultHandler is TypedActionHandlerEx) {
      defaultHandler.beforeExecute(editor, c, context, plan)
    }
  }

  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, true)
    try {
      defaultHandler.execute(editor, charTyped, dataContext)
    } finally {
      editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, null)
    }
  }
}
