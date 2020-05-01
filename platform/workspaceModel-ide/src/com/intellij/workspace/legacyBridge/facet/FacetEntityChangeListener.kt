// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.facet

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.workspace.api.EntityChange
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.FacetEntity
import com.intellij.workspace.api.ModuleEntity
import com.intellij.workspace.ide.WorkspaceModelChangeListener

internal class FacetEntityChangeListener(private val project: Project) {

  fun processBeforeChange(change: EntityChange<FacetEntity>) {
    when (change) {
      is EntityChange.Added -> {
        val manager = getFacetManager(change.entity.module) ?: return
        manager.publisher.beforeFacetAdded(manager.model.getOrCreateFacet(change.entity))
      }
      is EntityChange.Removed -> {
        val manager = getFacetManager(change.entity.module) ?: return
        val facet = manager.model.getFacet(change.entity) ?: return
        manager.publisher.beforeFacetRemoved(facet)
      }
      is EntityChange.Replaced -> {
        val manager = getFacetManager(change.newEntity.module) ?: return
        if (change.newEntity.name != change.oldEntity.name) {
          manager.publisher.beforeFacetRenamed(manager.model.getOrCreateFacet(change.newEntity))
        }
      }
    }
  }

  fun processChange(change: EntityChange<FacetEntity>) {
    when (change) {
      is EntityChange.Added -> {
        val manager = getFacetManager(change.entity.module) ?: return
        val facet = manager.model.getOrCreateFacet(change.entity)
        manager.model.updateEntity(change.entity, change.entity)
        FacetManagerBase.setFacetName(facet, change.entity.name)
        facet.initFacet()
        manager.model.facetsChanged()
        manager.publisher.facetAdded(facet)
      }
      is EntityChange.Removed -> {
        val manager = getFacetManager(change.entity.module) ?: return
        val facet = manager.model.removeEntity(change.entity) ?: return
        Disposer.dispose(facet)
        manager.model.facetsChanged()
        manager.publisher.facetRemoved(facet)
      }
      is EntityChange.Replaced -> {
        val manager = getFacetManager(change.newEntity.module) ?: return
        val facet = manager.model.updateEntity(change.oldEntity, change.newEntity) ?: return
        FacetManagerBase.setFacetName(facet, change.newEntity.name)
        manager.model.facetsChanged()
        if (change.oldEntity.name != change.newEntity.name) {
          manager.publisher.facetRenamed(facet, change.oldEntity.name)
        }
      }
    }
  }

  private val FacetManagerViaWorkspaceModel.publisher
    get() = module.messageBus.syncPublisher(FacetManager.FACETS_TOPIC)

  private fun getFacetManager(entity: ModuleEntity): FacetManagerViaWorkspaceModel? {
    val module = ModuleManager.getInstance(project).findModuleByName(entity.name) ?: return null
    return FacetManager.getInstance(module) as? FacetManagerViaWorkspaceModel
  }

  companion object {
    fun getInstance(project: Project) = project.service<FacetEntityChangeListener>()
  }
}