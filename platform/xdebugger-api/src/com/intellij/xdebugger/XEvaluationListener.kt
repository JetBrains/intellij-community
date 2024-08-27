// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XEvaluationListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<XEvaluationListener> = Topic(XEvaluationListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun inlineEvaluatorInvoked(session: XDebugSession, expression: XExpression) { }
}
