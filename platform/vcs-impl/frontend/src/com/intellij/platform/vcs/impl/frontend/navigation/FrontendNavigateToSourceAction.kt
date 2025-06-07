// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.navigation

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE_ARRAY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class FrontendNavigateToSourceAction(private val focusToEditor: Boolean = true) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val navigatables = NAVIGATABLE_ARRAY.getData(e.dataContext) ?: return
    navigatables.filterIsInstance<FrontendShelfNavigatable>().firstOrNull()?.navigate(focusToEditor)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = NAVIGATABLE_ARRAY.getData(e.dataContext).orEmpty().isNotEmpty()
  }
}