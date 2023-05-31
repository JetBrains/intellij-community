package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachToProcessView
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XAttachToProcessViewProvider {
  companion object {
    private val EP: ExtensionPointName<XAttachToProcessViewProvider> = ExtensionPointName.create(
      "com.intellij.xdebugger.dialog.process.view.provider")

    fun getProcessViews(
      project: Project,
      state: AttachDialogState,
      columnsLayout: AttachDialogColumnsLayout,
      attachDebuggerProviders: List<XAttachDebuggerProvider>
    ) = EP.extensions.map {
      it.getProcessView(project, state, columnsLayout, attachDebuggerProviders)
    }
  }

  fun getProcessView(
    project: Project,
    state: AttachDialogState,
    columnsLayout: AttachDialogColumnsLayout,
    attachDebuggerProviders: List<XAttachDebuggerProvider>
  ): AttachToProcessView
}