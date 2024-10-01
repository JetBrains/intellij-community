// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.project.impl

import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

const val PER_PROJECT_INSTANCE_TEST_SCRIPT: String = "test_script.txt"

private class SeparateProcessActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    if (!ProjectManagerEx.IS_CHILD_PROCESS) {
      return
    }

    // see com.jetbrains.thinclient.ThinClientActionsCustomizer

    // we don't remove this action in case some code uses it
    actionRegistrar.replaceAction("OpenFile", NewProjectActionDisabler())
    val fileOpenGroup = actionRegistrar.getActionOrStub("FileOpenGroup") as DefaultActionGroup
    fileOpenGroup.removeAll()
    actionRegistrar.unregisterAction("RecentProjectListGroup")
  }
}

private class NewProjectActionDisabler : OpenFileAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
  }
}

internal suspend fun checkChildProcess(projectStoreBaseDir: Path, options: OpenProjectTask): Boolean {
  val perProcessInstanceSupport = processPerProjectSupport()
  if (!perProcessInstanceSupport.isEnabled())
      return false

  if (!perProcessInstanceSupport.canBeOpenedInThisProcess(projectStoreBaseDir) && (ProjectManagerEx.getOpenProjects().isEmpty() || options.forceOpenInNewFrame)) {
    perProcessInstanceSupport.openInChildProcess(projectStoreBaseDir)


    blockingContext {
      application.invokeLater {
        ApplicationManagerEx.getApplicationEx().exit(true, true)
      }
    }

    return true
  }

  return false
}