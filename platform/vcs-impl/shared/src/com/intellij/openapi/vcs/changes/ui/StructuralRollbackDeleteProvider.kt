// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.annotations.ApiStatus

/**
 * Delete on the selected structural nodes reverts their changes. Delete of a file is not part of it, so a selection with a file gets null.
 * The Rollback action uses it too, so both revert only the selected changes.
 */
@ApiStatus.Internal
class StructuralRollbackDeleteProvider private constructor(private val nodes: List<ChangesBrowserStructuralNode<*>>) : DeleteProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun canDeleteElement(dataContext: DataContext): Boolean = true

  override fun deleteElement(dataContext: DataContext) {
    for ((rollback, group) in nodes.groupBy { requireNotNull(it.getRollback()) }) {
      rollback.rollback(group)
    }
  }

  companion object {
    /** Returns null unless all nodes are structural nodes with a rollback. */
    @JvmStatic
    fun create(nodes: List<ChangesBrowserNode<*>>): DeleteProvider? {
      val structuralNodes = nodes.map { it as? ChangesBrowserStructuralNode<*> ?: return null }
      if (structuralNodes.isEmpty() || structuralNodes.any { it.getRollback() == null }) return null
      return StructuralRollbackDeleteProvider(structuralNodes)
    }
  }
}
