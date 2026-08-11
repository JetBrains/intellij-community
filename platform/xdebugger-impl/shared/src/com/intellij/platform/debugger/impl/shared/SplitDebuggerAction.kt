// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SplitDebuggerAction : ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.FrontendOnly
}
