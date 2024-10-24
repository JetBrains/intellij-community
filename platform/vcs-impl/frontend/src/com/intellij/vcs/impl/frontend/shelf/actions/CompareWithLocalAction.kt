// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.impl.frontend.shelf.ShelfService
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree.Companion.GROUPED_CHANGES_KEY

class CompareWithLocalAction: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val dataContext = e.dataContext
    val selectedChangelists = GROUPED_CHANGES_KEY.getData(dataContext) ?: return
    ShelfService.getInstance(project).compareWithLocal(selectedChangelists)
  }
}