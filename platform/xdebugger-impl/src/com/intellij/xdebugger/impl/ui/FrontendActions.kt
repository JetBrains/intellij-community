// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.actions.StopAction
import com.intellij.execution.runners.FakeRerunAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy

internal class FrontendDebuggerToolbarStopAction : StopAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!useFeProxy()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

internal class FrontendDebuggerToolbarRerunAction : FakeRerunAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!useFeProxy()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}