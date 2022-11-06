package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.util.isAlive
import com.intellij.xdebugger.impl.util.onTermination

class AttachToProcessDialogFactory(private val project: Project) {

  companion object {
    val IS_LOCAL_VIEW_DEFAULT_KEY = DataKey.create<Boolean>("ATTACH_DIALOG_VIEW_TYPE")
    private fun isLocalViewDefault(dataContext: DataContext): Boolean = dataContext.getData(IS_LOCAL_VIEW_DEFAULT_KEY) ?: true
  }

  private var currentDialog: AttachToProcessDialog? = null

  fun showDialog(attachDebuggerProviders: List<XAttachDebuggerProvider>,
                 attachHosts: List<XAttachHostProvider<XAttachHost>>,
                 context: DataContext) {
    application.assertIsDispatchThread()
    val isLocalViewDefault = isLocalViewDefault(context)

    val currentDialogInstance = currentDialog
    if (currentDialogInstance != null && currentDialogInstance.isShowing && currentDialogInstance.disposable.isAlive) {
      currentDialogInstance.setShowLocalView(isLocalViewDefault)
      return
    }
    val dialog = AttachToProcessDialog(project, attachDebuggerProviders, attachHosts, isLocalViewDefault, null)
    dialog.disposable.onTermination {
      UIUtil.invokeLaterIfNeeded { if (currentDialog == dialog) currentDialog = null }
    }
    currentDialog = dialog
    dialog.show()
  }
}