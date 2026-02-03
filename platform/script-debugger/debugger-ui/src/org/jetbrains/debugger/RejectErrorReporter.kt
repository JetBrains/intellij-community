// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.errorIfNotMessage
import java.util.function.Consumer

@ApiStatus.Internal
class RejectErrorReporter @JvmOverloads constructor(
  private val session: XDebugSession,
  @NlsContexts.NotificationContent private val description: String? = null,
) : Consumer<Throwable> {
  override fun accept(error: Throwable) {
    if (thisLogger().errorIfNotMessage(error)) {
      session.reportError("${if (description == null) "" else "$description: "}${error.message}")
    }
  }
}