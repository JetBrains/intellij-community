// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

private class DumpWorkspaceEntitiesToClipboardAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToClipboardAsJson()
  }
}

private class DumpWorkspaceEntitiesToLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToLogAsJson()
  }
}

private class DumpWorkspaceEntitiesToLogFileAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToLogFileAsJson()
  }
}
