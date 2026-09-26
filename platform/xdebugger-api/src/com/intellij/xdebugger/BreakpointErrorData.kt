// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class BreakpointErrorData(
  @get:NlsContexts.DialogTitle
  val title: String,
  val message: String,
  val cause: Throwable?
)