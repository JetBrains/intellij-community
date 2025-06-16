// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.ide.actions.NonTrivialActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware

internal class VcsLogToolWindowDropdownActionGroup : NonTrivialActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
  }
}