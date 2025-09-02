// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun areFrontendDebuggerActionsEnabled(): Boolean {
  val frontendType = FrontendApplicationInfo.getFrontendType()
  return useFeProxy() ||
         (frontendType is FrontendType.RemoteDev && !frontendType.isLuxSupported) // CWM case
}