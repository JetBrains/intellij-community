// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.configuration

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EmptyLoggingService : HeadlessLogging.HeadlessLoggingService {
  private val emptyFlow = MutableSharedFlow<HeadlessLogging.LogEntry>()
  override fun logEntry(exception: HeadlessLogging.LogEntry) {
  }

  override fun loggingFlow(): SharedFlow<HeadlessLogging.LogEntry> {
    return emptyFlow
  }
}