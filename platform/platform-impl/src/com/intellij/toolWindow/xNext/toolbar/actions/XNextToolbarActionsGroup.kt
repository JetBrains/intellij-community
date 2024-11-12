// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.ide.actions.XNextHideAllToolWindowsAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class XNextToolbarActionsGroup : ActionGroup(), DumbAware {
  private val arrayOf = arrayOf<AnAction>(ActionManager.getInstance().getAction("SearchEverywhere"), XNextHideAllToolWindowsAction())

  override fun getChildren(e: AnActionEvent?): Array<out AnAction?> = arrayOf
}