// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.configuration

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeadlessLoggingServiceImpl : HeadlessLogging.HeadlessLoggingService {

  private val flow: MutableSharedFlow<HeadlessLogging.LogEntry> = MutableSharedFlow(replay = 0, extraBufferCapacity = 1024, onBufferOverflow = BufferOverflow.SUSPEND)


  override fun logEntry(logEntry: HeadlessLogging.LogEntry) {
    emitLogEntry(logEntry)
  }

  private fun emitLogEntry(entry: HeadlessLogging.LogEntry) {
    var internalLoggingPerformed = false
    while (!flow.tryEmit(entry)) {
      // we have some slow collectors
      // there is nothing the platform can do, other than report this incident
      if (!internalLoggingPerformed) {
        thisLogger().warn("Cannot log message: ${entry}. \n" +
                          "Headless logger has exhausted the buffer of messages. Please speed up the listeners of the Headless logger.")
        internalLoggingPerformed = true
      }
      // Nevertheless, it is important to deliver complete information to the user.
      Thread.sleep(100)
    }
  }

  override fun loggingFlow(): SharedFlow<HeadlessLogging.LogEntry> {
    return flow
  }
}