// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class SynchronizedProcessOutput : ProcessOutput() {
  private val finished = CompletableFuture<SynchronizedProcessOutput>()

  fun onFinished(): CompletionStage<SynchronizedProcessOutput> = finished.copy()

  // @formatter:off
  override fun appendStdout(text: String?)  = synchronized(this) { super.appendStdout(text) }
  override fun appendStderr(text: String?)  = synchronized(this) { super.appendStderr(text) }
  override fun getStdout(): String          = synchronized(this) { super.getStdout() }
  override fun getStderr(): String          = synchronized(this) { super.getStderr() }

  override fun checkSuccess(logger: Logger) = synchronized(this) { super.checkSuccess(logger) }

  override fun getExitCode(): Int           = synchronized(this) { super.getExitCode() }
  override fun isExitCodeSet(): Boolean     = synchronized(this) { super.isExitCodeSet() }
  override fun isTimeout(): Boolean         = synchronized(this) { super.isTimeout() }
  override fun isCancelled(): Boolean       = synchronized(this) { super.isCancelled() }

  override fun setExitCode(exitCode: Int)   = finish { super.setExitCode(exitCode) }
  override fun setTimeout()                 = finish { super.setTimeout() }
  override fun setCancelled()               = finish { super.setCancelled() }
  // @formatter:on

  private fun finish(block: () -> Unit) {
    synchronized(this) { block() }
    finished.complete(this)
  }
}