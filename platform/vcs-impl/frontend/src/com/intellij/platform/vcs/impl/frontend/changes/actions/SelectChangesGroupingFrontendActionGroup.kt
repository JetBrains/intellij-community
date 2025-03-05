// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.kernel.withLastKnownDb
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.frontend.changes.ChangesGroupingStatesHolder
import com.intellij.platform.vcs.impl.frontend.changes.ChangesTree
import com.intellij.platform.vcs.impl.shared.rhizome.RepositoryCountEntity
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.entity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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

@ApiStatus.Internal
abstract class SelectChangesGroupingAction(private val key: String) : ToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
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

@ApiStatus.Internal
class GroupByDirectoryAction() : SelectChangesGroupingAction("directory")

@ApiStatus.Internal
class GroupByModuleAction() : SelectChangesGroupingAction("module")

@ApiStatus.Internal
class GroupByRepositoryAction : SelectChangesGroupingAction("repository") {
  override fun update(e: AnActionEvent) {
    super.update(e)
    withLastKnownDb {
      val project = e.project ?: return@withLastKnownDb
      val projectEntity = entities(ProjectEntity.ProjectIdValue, project.projectId()).singleOrNull() ?: return@withLastKnownDb
      val repositoryCount = entity(RepositoryCountEntity.Project, projectEntity)?.count ?: 0
      e.presentation.isEnabledAndVisible = repositoryCount > 1
    }
  }
}