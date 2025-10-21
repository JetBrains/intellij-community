// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

// TODO: backend shouldn't make requests to show breakpoints dialog
//   but it is required now, since some BEControls may trigger breakpoints dialog showing
//   it means that backend should request frontend.
//   When all the be controls and LUX will be split, we can trigger this dialog showing on the frontend side.
@ApiStatus.Internal
val SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC: ProjectRemoteTopic<ShowBreakpointDialogRequest> = ProjectRemoteTopic("xdebugger.show.breakpoint.dialog", ShowBreakpointDialogRequest.serializer())

@ApiStatus.Internal
@Serializable
data class ShowBreakpointDialogRequest(val breakpointId: XBreakpointId?)