// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.xdebugger.XDebugSession
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.rpc.LOG
import java.util.function.Consumer

class RejectErrorReporter @JvmOverloads constructor(private val session: XDebugSession, private val description: String? = null) : Consumer<Throwable> {
  override fun accept(error: Throwable) {
    if (LOG.errorIfNotMessage(error)) {
      session.reportError("${if (description == null) "" else "$description: "}${error.message}")
    }
  }
}