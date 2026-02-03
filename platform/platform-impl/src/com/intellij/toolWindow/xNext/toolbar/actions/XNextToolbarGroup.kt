// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class XNextToolbarGroup : ActionGroup(), DumbAware {

  private val arrayOf = arrayOf<AnAction>(XNextToolbarToolWindowsGroup(), XNextToolWindowsMoreGroup(), XNextToolbarActionsGroup())
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return arrayOf
  }

}