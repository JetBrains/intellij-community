// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToBackend
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private class BoundedDocumentState(
  parentScope: CoroutineScope,
  initial: XExpressionDto,
  flow: Flow<XExpressionDto>,
) {
  private val xExpressionFlow = MutableStateFlow(initial)
  private val cs = parentScope.childScope(this.toString())

  init {
    cs.launch {
      flow.collectLatest {
        xExpressionFlow.value = it
      }
    }
  }

  val xExpression: XExpression get() = xExpressionFlow.value.xExpression()

  fun dispose() {
    cs.cancel()
  }
}

private class FrontendXDebuggerEditorsProvider(
  private val cs: CoroutineScope,
  private val fileTypeId: String,
  private val documentIdProvider: suspend (FrontendDocumentId, XExpressionDto, XSourcePositionDto?, EvaluationMode) -> XExpressionDocumentDto?,
) : XDebuggerEditorsProvider() {

  private val documentState = ConcurrentHashMap<Document, BoundedDocumentState>()

  override fun createDocument(project: Project, expression: XExpression, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document {
    return EditorFactory.getInstance().createDocument(expression.expression).also { document ->
      document.bindToBackend {
        onBindingDispose = {
          documentState.remove(document)?.dispose()
        }
        backendDocumentIdProvider = { frontendDocumentId ->
          val expressionDto = expression.toRpc()
          val documentDto = documentIdProvider(frontendDocumentId, expressionDto, sourcePosition?.toRpc(), mode)
          if (documentDto != null) {
            documentState[document] = BoundedDocumentState(cs, expressionDto, documentDto.expressionFlow.toFlow())
          }
          documentDto?.backendDocumentId
        }
        bindEditors = true
      }
    }
  }

  override fun getFileType(): FileType {
    val localFileType = FileTypeManager.getInstance().findFileTypeByName(fileTypeId)
    if (localFileType != null) {
      return localFileType
    }
    return Language.findLanguageByID("ThinClientLanguage")?.associatedFileType ?: FileTypes.PLAIN_TEXT
  }

  override fun createExpression(project: Project, document: Document, language: Language?, mode: EvaluationMode): XExpression {
    val xExpression = documentState[document]?.xExpression
    if (xExpression != null) return xExpression
    return super.createExpression(project, document, language, mode)
  }
}

internal fun getEditorsProvider(
  cs: CoroutineScope,
  editorsProviderDto: XDebuggerEditorsProviderDto,
  documentIdProvider: suspend (FrontendDocumentId, XExpressionDto, XSourcePositionDto?, EvaluationMode) -> XExpressionDocumentDto?,
): XDebuggerEditorsProvider {
  val localEditorsProvider = editorsProviderDto.editorsProvider
  if (localEditorsProvider != null) return localEditorsProvider
  return FrontendXDebuggerEditorsProvider(cs, editorsProviderDto.fileTypeId, documentIdProvider)
}