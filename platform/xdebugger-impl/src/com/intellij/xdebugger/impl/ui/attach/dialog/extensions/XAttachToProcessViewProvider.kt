// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
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
      attachDebuggerProviders: List<XAttachDebuggerProvider>,
      attachHostProviders: List<XAttachHostProvider<out XAttachHost>> = emptyList()
    ) = EP.extensions.mapNotNull {
      if (it.isApplicable(attachHostProviders))
        it.getProcessView(project, state, columnsLayout, attachDebuggerProviders, attachHostProviders)
      else
        null
    }
  }

  fun isApplicable(attachHostProviders: List<XAttachHostProvider<out XAttachHost>>) = true

  fun getProcessView(
    project: Project,
    state: AttachDialogState,
    columnsLayout: AttachDialogColumnsLayout,
    attachDebuggerProviders: List<XAttachDebuggerProvider>,
    attachHostProviders: List<XAttachHostProvider<out XAttachHost>> = emptyList()
  ): AttachToProcessView
}