// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl.actionholder

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction

internal sealed class ActionRef<T : AnAction> {
  abstract fun getAction(): T
}

private val manager by lazy { ActionManager.getInstance() }

internal fun <T : AnAction> createActionRef(action: T): ActionRef<T> {
  val id = manager.getId(action)
  return id?.let { IdActionRef(it) } ?: SimpleActionRef(action)
}

internal class IdActionRef<T : AnAction>(private val id: String) : ActionRef<T>() {
  override fun getAction(): T {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(manager.getAction(id)) { "There's no registered action with id=$id" } as T
  }
}

internal class SimpleActionRef<T : AnAction>(private val action: T) : ActionRef<T>() {
  override fun getAction() = action
}