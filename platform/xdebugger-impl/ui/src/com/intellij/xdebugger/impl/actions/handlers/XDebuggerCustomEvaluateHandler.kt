// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerCustomEvaluateHandler.Companion.EP_NAME
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * This API is intended to be used for languages and frameworks where debugging happens
 * without [com.intellij.xdebugger.XDebugSession] being initialized.
 *
 * So, this EP provides a way to show [XDebuggerEvaluationDialog] without having current [com.intellij.xdebugger.XDebugSession].
 */
@ApiStatus.Internal
interface XDebuggerCustomEvaluateHandler {
  companion object {
    internal val EP_NAME = ExtensionPointName<XDebuggerCustomEvaluateHandler>("com.intellij.xdebugger.customEvaluateHandler")
  }

  /**
   * @param dialogDisposable [Disposable] which will be disposed when evaluation dialog is closed
   */
  // TODO: can we avoid providing Disposable here?
  suspend fun createEvaluationDialogData(dialogDisposable: Disposable, project: Project, event: AnActionEvent): CustomEvaluationDialogData?

  fun canEvaluate(project: Project, event: AnActionEvent): Boolean
}

internal fun getAvailableCustomEvaluateHandler(project: Project, event: AnActionEvent): XDebuggerCustomEvaluateHandler? {
  return EP_NAME.extensionList.firstOrNull { it.canEvaluate(project, event) }
}

internal fun XDebuggerCustomEvaluateHandler.showCustomEvaluateDialog(project: Project, event: AnActionEvent) {
  val cs = project.service<XDebuggerCustomEvaluateHandlerProjectCoroutineScope>().cs
  cs.launch {
    withContext(Dispatchers.EDT) {
      val disposable = Disposer.newDisposable()
      val data = createEvaluationDialogData(disposable, project, event) ?: run {
        Disposer.dispose(disposable)
        return@withContext
      }
      val dialog = XDebuggerEvaluationDialog(data.evaluator, project, data.editorsProvider, data.text, data.sourcePosition, data.isCodeFragmentEvaluationSupported)
      Disposer.register(dialog.disposable, disposable)
      dialog.show()
    }
  }
}

@ApiStatus.Internal
data class CustomEvaluationDialogData(
  val evaluator: XDebuggerEvaluator,
  val editorsProvider: XDebuggerEditorsProvider,
  val text: XExpression,
  val sourcePosition: XSourcePosition?,
  val isCodeFragmentEvaluationSupported: Boolean,
)

@Service(Service.Level.PROJECT)
private class XDebuggerCustomEvaluateHandlerProjectCoroutineScope(val project: Project, val cs: CoroutineScope)