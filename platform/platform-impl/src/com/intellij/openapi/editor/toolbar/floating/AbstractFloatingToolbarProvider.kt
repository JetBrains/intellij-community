// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger

abstract class AbstractFloatingToolbarProvider(actionGroupId: String) : FloatingToolbarProvider {

  override val actionGroup: ActionGroup by lazy { resolveActionGroup(actionGroupId) }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
  }
}

private fun resolveActionGroup(actionGroupId: String): ActionGroup {
  val actionManager = ActionManager.getInstance()
  val action = actionManager.getAction(actionGroupId)
  if (action is ActionGroup) return action
  logger<FloatingToolbarProvider>().warn("Cannot initialize action group using (${action::class.java})")
  val defaultActionGroup = DefaultActionGroup()
  actionManager.registerAction(actionGroupId, defaultActionGroup)
  return defaultActionGroup
}
