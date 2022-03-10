package com.intellij.cce.processor

import com.intellij.cce.actions.Action

abstract class GenerateActionsProcessor : CodeFragmentProcessor {
  private val actions = mutableListOf<Action>()

  protected fun addAction(action: Action) {
    actions.add(action)
  }

  fun getActions() = actions
}