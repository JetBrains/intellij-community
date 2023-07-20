// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.attach.LocalAttachHost
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout

internal class AttachToLocalProcessView(
  project: Project,
  state: AttachDialogState,
  layout: AttachDialogColumnsLayout,
  debuggerProviders: List<XAttachDebuggerProvider>): AttachToProcessView(project, state, layout, debuggerProviders) {

  override suspend fun doUpdateProcesses() {
    collectAndShowItems(LocalAttachHost.INSTANCE)
  }

  override fun getHostType(): AttachDialogHostType = AttachDialogHostType.LOCAL

  override fun getViewActions(): List<AnAction> = emptyList()
}