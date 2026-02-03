// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.sendToClient
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object ShowImagePopupUtil {
  @ApiStatus.Internal
  @Serializable
  class Request(val imageData: ByteArray?)

  @ApiStatus.Internal
  val REMOTE_TOPIC: ProjectRemoteTopic<Request> = ProjectRemoteTopic("xdebugger.show.image.popup", Request.serializer())

  @JvmStatic
  fun showOnFrontend(project: Project, imageData: ByteArray?) {
    REMOTE_TOPIC.sendToClient(project, Request(imageData))
  }
}
