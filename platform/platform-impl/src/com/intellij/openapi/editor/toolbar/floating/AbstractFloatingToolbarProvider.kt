// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractFloatingToolbarProvider(actionGroupId: String) : FloatingToolbarProvider {

  override val actionGroup = resolveActionGroup(actionGroupId)

  private val toolbars = CopyOnWriteArrayList<FloatingToolbarComponent>()

  override fun register(toolbar: FloatingToolbarComponent, parentDisposable: Disposable) {
    toolbars.add(toolbar)
    Disposer.register(parentDisposable, Disposable { toolbars.remove(toolbar) })
  }

  fun updateAllToolbarComponents() {
    toolbars.forEach { it.update() }
  }

  companion object {

    private val LOG = Logger.getInstance("#com.intellij.openapi.editor.toolbar.floating")

    private fun resolveActionGroup(actionGroupId: String): ActionGroup {
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