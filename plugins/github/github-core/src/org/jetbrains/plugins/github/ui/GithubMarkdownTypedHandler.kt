// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion

internal class GithubMarkdownTypedHandler: TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (c == '@' && editor.getUserData(GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY) != null) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    }
    return Result.CONTINUE
  }
}
