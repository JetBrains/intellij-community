// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewInclusionModelApi
import com.intellij.platform.vcs.impl.shared.rpc.InclusionDto
import com.intellij.vcs.changes.viewModel.getRpcChangesView
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped

internal class ChangesViewInclusionModelApiImpl : ChangesViewInclusionModelApi {
  override suspend fun add(projectId: ProjectId, items: List<InclusionDto>) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion add: ${items.size} items" }
    withInclusionModel(project) { model ->
      val restored = restoreInclusion(project, items)
      if (restored.isNotEmpty()) model.addInclusion(restored)
    }
  }

  override suspend fun remove(projectId: ProjectId, items: List<InclusionDto>) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion remove: ${items.size} items" }
    withInclusionModel(project) { model ->
      val restored = restoreInclusion(project, items)
      if (restored.isNotEmpty()) model.removeInclusion(restored)
    }
  }

  override suspend fun set(projectId: ProjectId, items: List<InclusionDto>) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion set: ${items.size} items" }
    withInclusionModel(project) { model ->
      val restored = restoreInclusion(project, items)
      model.setInclusion(restored)
    }
  }

  override suspend fun retain(projectId: ProjectId, items: List<InclusionDto>) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion retain: ${items.size} items" }
    withInclusionModel(project) { model ->
      val restored = restoreInclusion(project, items)
      model.retainInclusion(restored)
    }
  }

  override suspend fun clear(projectId: ProjectId) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion clear" }
    withInclusionModel(project) { model ->
      model.clearInclusion()
    }
  }

  override suspend fun notifyInclusionUpdateApplied(projectId: ProjectId) = projectScoped(projectId) { project ->
    LOG.trace { "Inclusion update applied" }
    project.getRpcChangesView().inclusionChanged()
  }


  private suspend fun withInclusionModel(project: Project, action: (InclusionModel) -> Unit) {
    val changesViewModel = project.getRpcChangesView()
    val inclusionModel = changesViewModel.inclusionModel.value ?: return
    action(inclusionModel)
  }

  private fun restoreInclusion(project: Project, inclusion: List<InclusionDto>): List<Any> {
    val changeIdCache = ChangeListChangeIdCache.getInstance(project)
    return inclusion.mapNotNull { inclusionItem ->
      when (inclusionItem) {
        is InclusionDto.Change -> changeIdCache.getChange(inclusionItem.changeId).also { change ->
          if (change == null) {
            LOG.warn("Change for id ${inclusionItem.changeId} not found in cache")
          }
        }
        is InclusionDto.File -> inclusionItem.path.filePath
      }
    }
  }

  companion object {
    private val LOG = logger<ChangesViewInclusionModelApiImpl>()
  }
}
