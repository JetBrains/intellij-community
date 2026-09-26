package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachToProcessViewProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AttachToRemoteProcessViewProvider : XAttachToProcessViewProvider {
  override fun isApplicable(attachHostProviders: List<XAttachHostProvider<out XAttachHost>>) = attachHostProviders.isNotEmpty()

  override fun getProcessView(project: Project,
                              state: AttachDialogState,
                              columnsLayout: AttachDialogColumnsLayout,
                              attachDebuggerProviders: List<XAttachDebuggerProvider>,
                              attachHostProviders: List<XAttachHostProvider<out XAttachHost>>): AttachToProcessView {
    return AttachToRemoteProcessView(project, state, columnsLayout, attachHostProviders, attachDebuggerProviders)
  }
}