// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.actions.Action
import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.core.CodeFragment

abstract class ActionGenerator : CodeFragmentProcessor {
  protected lateinit var actionsBuilder: ActionsBuilder

  suspend fun buildActions(code: CodeFragment): List<Action> {
    actionsBuilder = ActionsBuilder()
    processFragment(code)
    return actionsBuilder.build()
  }
}

abstract class AsyncActionGenerator : ActionGenerator() {
  open suspend fun process(code: CodeFragment): Unit = Unit

  final override suspend fun processFragment(code: CodeFragment): Unit = process(code)

  protected suspend fun actions(init: suspend ActionsBuilder.() -> Unit) {
    init(actionsBuilder)
  }
}

abstract class GenerateActionsProcessor : ActionGenerator() {
  open fun process(code: CodeFragment): Unit = Unit

  final override suspend fun processFragment(code: CodeFragment): Unit = process(code)

  protected fun actions(init: ActionsBuilder.() -> Unit) {
    init(actionsBuilder)
  }
}