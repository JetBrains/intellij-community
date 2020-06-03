// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.google.common.collect.HashBiMap
import com.intellij.facet.Facet
import com.intellij.facet.FacetManagerImpl
import com.intellij.facet.ModifiableFacetModel
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.subFacets
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge

internal class ModifiableFacetModelBridge(private val initialStorage: WorkspaceEntityStorage,
                                          private val diff: WorkspaceEntityStorageDiffBuilder,
                                          moduleBridge: ModuleBridge,
                                          private val facetManager: FacetManagerBridge)
  : FacetModelBridge(moduleBridge), ModifiableFacetModel {
  private val listeners: MutableList<ModifiableFacetModel.Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  init {
    populateFrom(facetManager.model)
  }

  private fun getModuleEntity() = initialStorage.resolve(moduleBridge.moduleEntityId)!!

  override fun addFacet(facet: Facet<*>) {
    addFacet(facet, null)
  }

  override fun addFacet(facet: Facet<*>, externalSource: ProjectModelExternalSource?) {
    val moduleEntity = getModuleEntity()
    val moduleSource = moduleEntity.entitySource
    val source = when {
      moduleSource is JpsFileEntitySource && externalSource != null ->
        JpsImportedEntitySource(moduleSource, externalSource.id, moduleBridge.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource != null && moduleSource.externalSystemId != externalSource.id ->
        JpsImportedEntitySource(moduleSource.internalFile, externalSource.id, moduleBridge.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource == null ->
        moduleSource.internalFile
      else -> moduleSource
    }
    val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
    val underlyingEntity = facet.underlyingFacet?.let { entityToFacet.inverse()[it]!! }
    val entity = diff.addFacetEntity(facet.name, facet.type.stringId, facetConfigurationXml, moduleEntity, underlyingEntity, source)
    entityToFacet[entity] = facet
    FacetManagerImpl.setExternalSource(facet, externalSource)
    facetsChanged()
  }

  override fun removeFacet(facet: Facet<*>?) {
    val facetEntity = entityToFacet.inverse()[facet] ?: return
    removeFacetEntityWithSubFacets(facetEntity)
    facetsChanged()
  }

  override fun replaceFacet(original: Facet<*>, replacement: Facet<*>) {
    removeFacet(original)
    addFacet(replacement)
  }

  private fun removeFacetEntityWithSubFacets(entity: FacetEntity) {
    if (entity !in entityToFacet) return

    entity.subFacets.forEach {
      removeFacetEntityWithSubFacets(it)
    }
    entityToFacet.remove(entity)
    diff.removeEntity(entity)
  }

  override fun rename(facet: Facet<*>, newName: String) {
    val entity = entityToFacet.inverse()[facet]!!
    val newEntity = diff.modifyEntity(ModifiableFacetEntity::class.java, entity) {
      this.name = newName
    }
    entityToFacet.inverse()[facet] = newEntity
    facetsChanged()
  }

  override fun getNewName(facet: Facet<*>): String? {
    val entity = entityToFacet.inverse()[facet]!!
    return entity.name
  }

  override fun commit() {
    val moduleDiff = moduleBridge.diff
    if (moduleDiff != null) {
      val res = moduleDiff.addDiff(diff)
      populateModel(res)
    }
    else {
      WorkspaceModel.getInstance(moduleBridge.project).updateProjectModel {
        val res = it.addDiff(diff)
        populateModel(res)
      }
    }
  }

  private fun populateModel(replaceMap: Map<WorkspaceEntity, WorkspaceEntity>) {
    val mapInNewStore: HashBiMap<FacetEntity, Facet<*>> = HashBiMap.create()
    entityToFacet.forEach { (key, value) -> mapInNewStore[replaceMap.getOrDefault(key, key) as FacetEntity] = value }
    facetManager.model.populateFrom(mapInNewStore)
  }

  override fun isModified(): Boolean {
    return !diff.isEmpty()
  }

  override fun isNewFacet(facet: Facet<*>): Boolean {
    val entity = entityToFacet.inverse()[facet]
    return entity != null && initialStorage.resolve(entity.persistentId()) == null
  }

  override fun addListener(listener: ModifiableFacetModel.Listener, parentDisposable: Disposable) {
    listeners += listener
    Disposer.register(parentDisposable, Disposable { listeners -= listener })
  }

  override fun facetsChanged() {
    super.facetsChanged()
    listeners.forEach { it.onChanged() }
  }
}