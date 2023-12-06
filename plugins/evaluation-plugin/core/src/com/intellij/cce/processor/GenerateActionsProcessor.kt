// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.actions.Action

abstract class GenerateActionsProcessor : CodeFragmentProcessor {
  private val actions = mutableListOf<Action>()

  protected fun addAction(action: Action) {
    actions.add(action)
  }

  fun getActions() = actions

  fun clear() {
    actions.clear()
  }

}