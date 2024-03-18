// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.psiViewer.debug

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.memory.action.DebuggerTreeAction
import com.intellij.dev.psiViewer.PsiViewerDialog
import com.intellij.java.dev.JavaDevBundle
import com.intellij.lang.Language
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.ObjectReference

private val LOG = Logger.getInstance(PsiViewerDebugAction::class.java)
private const val PSI_ELEMENT = "com.intellij.psi.PsiElement"
private const val GET_NAME = "getName"

class PsiViewerDebugAction : DebuggerTreeAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    val refType = getObjectReference(node)?.referenceType()
    return DebuggerUtils.instanceOf(refType, PSI_ELEMENT)
  }

  override fun perform(
    node: XValueNodeImpl,
    nodeName: @NlsSafe String,
    e: AnActionEvent
  ) {
    val project = e.project ?: return
    val debugProcess = JavaDebugProcess.getCurrentDebugProcess(e)
    val suspendContext = debugProcess?.suspendManager?.getPausedContext()
    debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(suspendContext) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        try {
          val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)

          val psiElemObj = getObjectReference(node) ?: return
          val psiFileObj = debugProcess.invokeMethod(psiElemObj, GET_CONTAINING_FILE, evalContext) as? ObjectReference ?: return
          val fileText = psiFileObj.getText(debugProcess, evalContext) ?: return
          val fileName = DebuggerUtils.getValueAsString(evalContext, debugProcess.invokeMethod(psiFileObj, GET_NAME, evalContext))
          val psiRangeInFile = psiElemObj.getTextRange(debugProcess, evalContext)
          val languageId = psiFileObj.getLanguageId(debugProcess, evalContext)
          val language = Language.findLanguageByID(languageId) ?: return

          DebuggerUIUtil.invokeLater {
            val editorFactory = EditorFactory.getInstance()
            val document = editorFactory.createDocument(fileText)
            val editor = editorFactory.createEditor(document) as EditorEx
            if (psiRangeInFile != null) editor.selectionModel.setSelection(psiRangeInFile.startOffset, psiRangeInFile.endOffset)

            fun showDialog() {
              val dialog = PsiViewerDialog(project, editor)
              dialog.show()
            }

            fun showDebugTab() {
              val debugSession = debugProcess.session?.xDebugSession ?: return
              val runnerLayoutUi = debugSession.ui ?: return
              val psiViewerPanel = PsiViewerDebugPanel(project, editor, language, node.name!!, debugSession, fileName)
              val id = PsiViewerDebugPanel.getTitle(nodeName, PsiViewerDebugSettings.getInstance().watchMode)
              val content = runnerLayoutUi.createContent(id, psiViewerPanel, id, null, null)
              runnerLayoutUi.addContent(content)
              runnerLayoutUi.selectAndFocus(content, true, true)
            }

            if (PsiViewerDebugSettings.getInstance().showDialogFromDebugAction) showDialog() else showDebugTab()
          }
        } catch (e: EvaluateException) {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(
            JavaDevBundle.message("psi.viewer.debug.evaluation.failed"), NotificationType.ERROR
          )
          LOG.warn("Failed to evaluate PSI expression", e)
        }
      }
    })
  }
}