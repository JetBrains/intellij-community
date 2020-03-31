// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport

class SelectChangesGroupingActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(ChangesGroupingSupport.KEY) != null
  }
}