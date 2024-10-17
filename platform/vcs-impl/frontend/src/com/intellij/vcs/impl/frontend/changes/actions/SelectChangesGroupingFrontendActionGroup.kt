// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.project.asEntity
import com.intellij.vcs.impl.frontend.changes.ChangesGroupingStatesHolder
import com.intellij.vcs.impl.frontend.changes.ChangesTree
import com.intellij.vcs.impl.shared.rhizome.RepositoryCountEntity
import com.jetbrains.rhizomedb.asOf
import com.jetbrains.rhizomedb.entity
import fleet.kernel.rete.Rete

class SelectChangesGroupingFrontendActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val fromActionToolbar = e.isFromActionToolbar
    e.presentation.isPopupGroup = fromActionToolbar
    e.presentation.isEnabled = e.getData(ChangesTree.GROUPING_SUPPORT_KEY) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

abstract class SelectChangesGroupingAction(private val key: String) : ToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project ?: return
    e.presentation.isEnabledAndVisible = ChangesGroupingStatesHolder.getInstance(project).allGroupingKeys.contains(key)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(ChangesTree.GROUPING_SUPPORT_KEY)?.get(key) == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(ChangesTree.GROUPING_SUPPORT_KEY)?.set(key, state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class GroupByDirectoryAction() : SelectChangesGroupingAction("directory")

class GroupByModuleAction() : SelectChangesGroupingAction("module")

class GroupByRepositoryAction : SelectChangesGroupingAction("repository") {
  override fun update(e: AnActionEvent) {
    super.update(e)
    asOf(KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext[Rete]!!.lastKnownDb.value) {
      val projectEntity = e.project?.asEntity() ?: return@asOf
      val repositoryCount = entity(RepositoryCountEntity.Project, projectEntity)?.count ?: 0
      e.presentation.isEnabledAndVisible = repositoryCount > 1
    }
  }
}