package com.intellij.dev.psiViewer

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.memory.action.DebuggerTreeAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.text.findTextRange
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.ObjectReference

private val LOG = Logger.getInstance(PsiViewerDebugAction::class.java)

private val PSI_ELEMENT = "com.intellij.psi.PsiElement"

private val GET_CONTAINING_FILE = "getContainingFile"

private val GET_TEXT = "getText"

class PsiViewerDebugAction : DebuggerTreeAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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
    val suspendContext = debugProcess?.getSuspendManager()?.getPausedContext()
    debugProcess?.getManagerThread()?.schedule(object : SuspendContextCommandImpl(suspendContext) {
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

          val psiElemObj = getObjectReference(node) ?: return
          val psiFileObj = psiElemObj.invokeMethod(GET_CONTAINING_FILE) ?: return
          val psiText = psiElemObj.getText() ?: return
          val fileText = psiFileObj.getText() ?: return
          val psiRangeInFile = fileText.findTextRange(psiText)

          DebuggerUIUtil.invokeLater {
            val editorFactory = EditorFactory.getInstance()
            val document = editorFactory.createDocument(fileText)
            val editor = editorFactory.createEditor(document) as EditorEx
            if (psiRangeInFile != null) editor.selectionModel.setSelection(psiRangeInFile.startOffset, psiRangeInFile.endOffset)

            fun showDialog() {
              val dialog = PsiViewerDialog(project, editor)
              dialog.show()
            }

            showDialog()
          }
        } catch (e: EvaluateException) {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(
            DevPsiViewerBundle.message("debug.evaluation.failed"), NotificationType.ERROR
          )
          LOG.error("Failed to evaluate PSI expression", e)
        }
      }
    })
  }
}