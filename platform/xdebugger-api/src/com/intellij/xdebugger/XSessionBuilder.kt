// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents the result of a successfully started debugging session.
 *
 * This interface provides access to the associated debugging session
 * and an optional content descriptor related to the session.
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface XSessionStartedResult {
  val session: XDebugSession
  val runContentDescriptor: RunContentDescriptor?
}

/**
 * Builder interface for creating and configuring a debugging session.
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface XSessionBuilder {
  @Throws(ExecutionException::class)
  fun startSession(): XSessionStartedResult

  fun environment(environment: ExecutionEnvironment): XSessionBuilder
  fun sessionName(@Nls sessionName: @Nls String): XSessionBuilder
  fun icon(icon: Icon?): XSessionBuilder
  fun contentToReuse(contentToReuse: RunContentDescriptor?): XSessionBuilder
  fun showTab(value: Boolean): XSessionBuilder
  fun showToolWindowOnSuspendOnly(value: Boolean): XSessionBuilder
}
