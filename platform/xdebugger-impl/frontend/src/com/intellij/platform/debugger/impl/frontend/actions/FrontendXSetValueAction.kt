// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.ui.tree.actions.XSetValueAction

private class FrontendXSetValueAction : XSetValueAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

}