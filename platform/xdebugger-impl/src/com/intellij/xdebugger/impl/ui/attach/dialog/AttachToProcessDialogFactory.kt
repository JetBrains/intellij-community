// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.util.isAlive
import com.intellij.xdebugger.impl.util.onTermination
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class AttachToProcessDialogFactory(private val project: Project) {
  companion object {
    // used externally
    @Suppress("MemberVisibilityCanBePrivate")
    val DEFAULT_VIEW_HOST_TYPE: DataKey<AttachDialogHostType> = DataKey.create("ATTACH_DIALOG_VIEW_HOST_TYPE")
    private fun getDefaultViewHostType(dataContext: DataContext): AttachDialogHostType =
      dataContext.getData(DEFAULT_VIEW_HOST_TYPE) ?: AttachDialogHostType.LOCAL
  }

  private var currentDialog: AttachToProcessDialog? = null

  fun showDialog(attachDebuggerProviders: List<XAttachDebuggerProvider>,
                 attachHosts: List<XAttachHostProvider<XAttachHost>>,
                 context: DataContext) {
    ThreadingAssertions.assertEventDispatchThread()
    val defaultViewHostType = getDefaultViewHostType(context)

    val currentDialogInstance = getOpenDialog()
    if (currentDialogInstance != null) {
      currentDialogInstance.setAttachView(defaultViewHostType)
      return
    }

    val dialog = AttachToProcessDialog(project, attachDebuggerProviders, attachHosts, context, defaultViewHostType, null)
    dialog.disposable.onTermination {
      UIUtil.invokeLaterIfNeeded { if (currentDialog == dialog) currentDialog = null }
    }
    currentDialog = dialog
    dialog.show()
  }

  fun getOpenDialog(): AttachToProcessDialog? =
    currentDialog?.takeIf { it.isShowing && it.disposable.isAlive }
}