// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiViewer.debug

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.memory.action.DebuggerTreeAction
import com.intellij.dev.psiViewer.PsiViewerDialog
import com.intellij.java.devkit.psiViewer.JavaPsiViewerBundle
import com.intellij.lang.Language
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.findTextRange
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.ObjectReference

private val LOG = Logger.getInstance(PsiViewerDebugAction::class.java)
private const val PSI_ELEMENT = "com.intellij.psi.PsiElement"
private const val GET_CONTAINING_FILE = "getContainingFile"
private const val GET_TEXT = "getText"
private const val GET_LANGUAGE = "getLanguage"
private const val GET_ID = "getID"

class PsiViewerDebugAction : DebuggerTreeAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    val refType = getObjectReference(node)?.referenceType()
    return DebuggerUtils.instanceOf(refType, PSI_ELEMENT)
  }

  override fun perform(
    node: XValueNodeImpl,
    nodeName: String,
    e: AnActionEvent
  ) {
    val project = e.project ?: return
    val debugProcess = JavaDebugProcess.getCurrentDebugProcess(e)
    val suspendContext = debugProcess?.suspendManager?.getPausedContext()
    debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(suspendContext) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        try {
          val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)

          fun ObjectReference.invokeMethod(methodName: String): ObjectReference? {
            val method = DebuggerUtils.findMethod(referenceType(), methodName, null) ?: return null
            return debugProcess.invokeMethod(evalContext, this, method, emptyList()) as? ObjectReference
          }

          fun ObjectReference.getText(): String? {
            val stringObj = invokeMethod(GET_TEXT)
            return DebuggerUtils.getValueAsString(evalContext, stringObj) ?: return null
          }

          fun ObjectReference.getLanguageId(): String? {
            val languageObj = invokeMethod(GET_LANGUAGE) ?: return null
            val stringObj = languageObj.invokeMethod(GET_ID) ?: return null
            return DebuggerUtils.getValueAsString(evalContext, stringObj) ?: return null
          }

          val psiElemObj = getObjectReference(node) ?: return
          val psiFileObj = psiElemObj.invokeMethod(GET_CONTAINING_FILE) ?: return
          val psiText = psiElemObj.getText() ?: return
          val fileText = psiFileObj.getText() ?: return
          val psiRangeInFile = fileText.findTextRange(psiText)
          val languageId = psiFileObj.getLanguageId()
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
              val runnerLayoutUi = debugProcess.session?.xDebugSession?.ui ?: return
              val psiViewerPanel = PsiViewerDebugPanel(project, editor, language)
              val id = "${node.name} ${DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis())}"
              val content = runnerLayoutUi.createContent(id, psiViewerPanel, id, null, null)
              runnerLayoutUi.addContent(content)
              runnerLayoutUi.selectAndFocus(content, true, true)
            }

            if (PsiViewerDebugSettings.getInstance().showDialogFromDebugAction) showDialog() else showDebugTab()
          }
        } catch (e: EvaluateException) {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(
            JavaPsiViewerBundle.message("psi.viewer.debug.evaluation.failed"), NotificationType.ERROR
          )
          LOG.error("Failed to evaluate PSI expression", e)
        }
      }
    })
  }
}