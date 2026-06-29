// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PiControlRequestHandler {
  val messageType: String

  fun handle(
    context: PiControlSessionContext,
    request: PiControlExtensionRequest,
    requestId: String,
    sendResponse: (String) -> Unit,
  )

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PiControlRequestHandler> =
      ExtensionPointName("com.intellij.agent.workbench.pi.controlRequestHandler")
  }
}
