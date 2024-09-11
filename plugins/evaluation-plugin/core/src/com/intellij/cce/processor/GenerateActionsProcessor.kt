// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.actions.Action
import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.actions.SessionLimitReachedException
import com.intellij.cce.core.CodeFragment
import com.intellij.openapi.diagnostic.logger

abstract class GenerateActionsProcessor : CodeFragmentProcessor {
  private val LOG = logger<GenerateActionsProcessor>()

  private lateinit var actionsBuilder: ActionsBuilder

  protected fun actions(init: ActionsBuilder.() -> Unit) {
    init(actionsBuilder)
  }

  fun buildActions(code: CodeFragment, sessionLimit: Int): List<Action> {
    actionsBuilder = ActionsBuilder(sessionLimit)
    try {
       process(code)
    } catch (t: SessionLimitReachedException) {
      LOG.warn("session limit reached. process only first $sessionLimit sessions")
    }

    return actionsBuilder.build()
  }
}
