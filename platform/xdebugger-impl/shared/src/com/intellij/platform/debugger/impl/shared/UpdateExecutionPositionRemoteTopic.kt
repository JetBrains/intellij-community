// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val UPDATE_EXECUTION_POSITION_REMOTE_TOPIC: ProjectRemoteTopic<XDebugSessionId> =
  ProjectRemoteTopic("xdebugger.update.execution.position", XDebugSessionId.serializer())
