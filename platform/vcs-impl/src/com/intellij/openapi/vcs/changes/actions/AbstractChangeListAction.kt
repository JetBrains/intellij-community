// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList

abstract class AbstractChangeListAction : DumbAwareAction() {
  protected fun getChangeLists(e: AnActionEvent): Sequence<LocalChangeList> {
    val changeListManager = ChangeListManager.getInstance(e.project ?: return emptySequence())

    val changeLists = e.getData(VcsDataKeys.CHANGE_LISTS)
    if (!changeLists.isNullOrEmpty()) return changeLists.asSequence().mapNotNull { changeListManager.findChangeList(it.name) }

    val changes = e.getData(VcsDataKeys.CHANGES)
    return changes.orEmpty().asSequence().mapNotNull { changeListManager.getChangeList(it) }.distinct()
  }

  protected fun getTargetChangeList(e: AnActionEvent): LocalChangeList? = getChangeLists(e).singleOrNull()
}