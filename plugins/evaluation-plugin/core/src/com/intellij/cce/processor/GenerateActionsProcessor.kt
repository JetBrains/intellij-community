// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.actions.ActionsBuilder

abstract class GenerateActionsProcessor : CodeFragmentProcessor {
  private lateinit var actionsBuilder: ActionsBuilder

  protected fun actions(init: ActionsBuilder.() -> Unit): ActionsBuilder {
    actionsBuilder = ActionsBuilder().apply(init)
    return actionsBuilder
  }

  fun getActions() = actionsBuilder.build()
}
