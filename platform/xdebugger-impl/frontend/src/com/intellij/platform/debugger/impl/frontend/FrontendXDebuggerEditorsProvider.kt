// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

// TODO[IJPL-160146]: this is really dummy solution for EditorsProvider,
//  most likely new api should be introduced to support remote dev
internal class FrontendXDebuggerEditorsProvider : XDebuggerEditorsProvider() {
  override fun createDocument(project: Project, expression: XExpression, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document {
    return EditorFactory.getInstance().createDocument(expression.expression)
  }

  override fun getFileType(): FileType {
    return Language.findLanguageByID("ThinClientLanguage")?.associatedFileType ?: FileTypes.PLAIN_TEXT
  }
}