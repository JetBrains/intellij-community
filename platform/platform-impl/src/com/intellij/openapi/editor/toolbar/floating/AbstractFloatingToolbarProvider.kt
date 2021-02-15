// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger

abstract class AbstractFloatingToolbarProvider(actionGroupId: String) : FloatingToolbarProvider {

  override val actionGroup by lazy { resolveActionGroup(actionGroupId) }

  open fun register(component: FloatingToolbarComponent, parentDisposable: Disposable) {}

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    register(component, parentDisposable)
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.editor.toolbar.floating")

    fun resolveActionGroup(actionGroupId: String): ActionGroup {
      val actionManager = ActionManager.getInstance()
      val action = actionManager.getAction(actionGroupId)
      if (action is ActionGroup) return action
      LOG.warn("Cannot initialize action group using (${action::class.java})")
      val defaultActionGroup = DefaultActionGroup()
      actionManager.registerAction(actionGroupId, defaultActionGroup)
      return defaultActionGroup
    }
  }
}