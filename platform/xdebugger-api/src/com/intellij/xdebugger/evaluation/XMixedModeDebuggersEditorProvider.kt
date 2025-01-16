// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.mixedMode.XMixedModeDebugProcess
import org.jetbrains.annotations.Unmodifiable

class XMixedModeDebuggersEditorProvider(
  val session: XDebugSession,
  val lowDebuggerEditorsProvider: XDebuggerEditorsProvider,
  val highDebuggerEditorsProvider: XDebuggerEditorsProvider,
) : XDebuggerEditorsProvider() {
  init {
    assert(session.isMixedMode)
  }

  override fun getFileType(): FileType = getActiveProvider().fileType

  override fun createDocument(project: Project, text: String, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document =
    getActiveProvider().createDocument(project, text, sourcePosition, mode)

  override fun createDocument(project: Project, expression: XExpression, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document =
    getActiveProvider().createDocument(project, expression, sourcePosition, mode)

  override fun afterEditorCreated(editor: Editor?) {
    getActiveProvider().afterEditorCreated(editor)
  }

  override fun getSupportedLanguages(project: Project, sourcePosition: XSourcePosition?): @Unmodifiable Collection<Language?> =
    getActiveProvider().getSupportedLanguages(project, sourcePosition)

  override fun createExpression(project: Project, document: Document, language: Language?, mode: EvaluationMode): XExpression =
    getActiveProvider().createExpression(project, document, language, mode)

  override fun getInlineDebuggerHelper(): InlineDebuggerHelper = getActiveProvider().inlineDebuggerHelper

  override fun isEvaluateExpressionFieldEnabled(): Boolean = getActiveProvider().isEvaluateExpressionFieldEnabled

  private fun getActiveProvider(): XDebuggerEditorsProvider {
    val file = session.currentStackFrame?.sourcePosition?.file ?: return highDebuggerEditorsProvider
    return if ((session.getDebugProcess(true) as XMixedModeDebugProcess).belongsToMe(file))
      lowDebuggerEditorsProvider
    else
      highDebuggerEditorsProvider
  }
}