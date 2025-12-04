// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionBuilder
import com.intellij.xdebugger.XSessionStartedResult
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class XSessionStartedResultImpl(
  override val session: XDebugSession,
  override val runContentDescriptor: RunContentDescriptor?,
) : XSessionStartedResult

internal class SessionStartParams(
  val starter: XDebugProcessStarter,
  val environment: ExecutionEnvironment?,
  val sessionName: @Nls String?,
  val icon: Icon?,
  val contentToReuse: RunContentDescriptor?,
  val isShowTab: Boolean,
  val isShowToolWindowOnSuspendOnly: Boolean,
) {
  init {
    if (isShowTab) {
      requireNotNull(sessionName) { "Session name must be provided when showTab is true" }
    }
    else {
      requireNotNull(environment) { "Environment must be provided when showTab is false" }
    }
  }
}

internal class XDebugSessionBuilderImpl(
  private val manager: XDebuggerManagerImpl,
  private val starter: XDebugProcessStarter,
) : XDebugSessionBuilder {
  private var myEnvironment: ExecutionEnvironment? = null
  private var mySessionName: @Nls String? = null
  private var myIcon: Icon? = null
  private var myContentToReuse: RunContentDescriptor? = null
  private var myShowTab = false
  private var myShowToolWindowOnSuspendOnly = false

  override fun environment(environment: ExecutionEnvironment): XDebugSessionBuilderImpl {
    myEnvironment = environment
    return this
  }

  override fun sessionName(@Nls sessionName: @Nls String): XDebugSessionBuilderImpl {
    mySessionName = sessionName
    return this
  }

  override fun icon(icon: Icon?): XDebugSessionBuilderImpl {
    myIcon = icon
    return this
  }

  override fun contentToReuse(contentToReuse: RunContentDescriptor?): XDebugSessionBuilderImpl {
    myContentToReuse = contentToReuse
    return this
  }

  override fun showTab(value: Boolean): XDebugSessionBuilderImpl {
    myShowTab = value
    return this
  }

  override fun showToolWindowOnSuspendOnly(value: Boolean): XDebugSessionBuilderImpl {
    myShowToolWindowOnSuspendOnly = value
    return this
  }

  override fun startSession(): XSessionStartedResult {
    val params = SessionStartParams(starter, myEnvironment, mySessionName, myIcon, myContentToReuse, myShowTab, myShowToolWindowOnSuspendOnly)
    return manager.startSession(params)
  }
}