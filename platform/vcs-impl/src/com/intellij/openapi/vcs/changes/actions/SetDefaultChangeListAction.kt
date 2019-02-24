// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.ChangeListManager

class SetDefaultChangeListAction : AbstractChangeListAction() {
  override fun update(e: AnActionEvent) {
    val changeList = getTargetChangeList(e)
    val enabled = changeList != null && !changeList.isDefault

    updateEnabledAndVisible(e, enabled)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changeList = getTargetChangeList(e)

    if (changeList != null) {
      ChangeListManager.getInstance(project).defaultChangeList = changeList
    }
  }
}
