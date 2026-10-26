// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val FRAME_NOTIFICATION_REMOTE_TOPIC: ProjectRemoteTopic<FrameNotificationRequest> =
  ProjectRemoteTopic("xdebugger.show.frame.notification", FrameNotificationRequest.serializer())
