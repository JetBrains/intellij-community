// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.SplitDebuggerMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SplitDebuggerAction : ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior = getSplitBehavior()

  companion object {
    fun getSplitBehavior(): ActionRemoteBehavior {
      // Keep CWM with the old scheme
      val frontendType = FrontendApplicationInfo.getFrontendType()
      if (frontendType is FrontendType.Remote && frontendType.isGuest()) return ActionRemoteBehavior.FrontendOtherwiseBackend
      return if (SplitDebuggerMode.isSplitDebugger()) ActionRemoteBehavior.FrontendOnly else ActionRemoteBehavior.BackendOnly
    }
  }
}
