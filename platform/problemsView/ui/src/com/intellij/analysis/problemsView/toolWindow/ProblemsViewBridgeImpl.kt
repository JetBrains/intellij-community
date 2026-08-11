// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.daemon.impl.ProblemsViewBridge
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProblemsViewBridgeImpl : ProblemsViewBridge {
  override fun toggleCurrentFileProblems(project: Project, virtualFile: VirtualFile?, document: Document?) {
    ProblemsView.toggleCurrentFileProblems(project, virtualFile, document)
  }

  override fun selectHighlighterIfVisible(project: Project, highlighter: RangeHighlighterEx) {
    ProblemsView.selectHighlighterIfVisible(project, highlighter)
  }
}
