// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowId

class TerminalRunSmartCommandAction(override val executor: Executor = DefaultRunExecutor.getRunExecutorInstance()) : TerminalExecutorAction()

class TerminalDebugSmartCommandAction(override val executor: Executor? = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG))
  : TerminalExecutorAction()

abstract class TerminalExecutorAction : AnAction() {
  abstract val executor: Executor?

  override fun actionPerformed(e: AnActionEvent) {}
}