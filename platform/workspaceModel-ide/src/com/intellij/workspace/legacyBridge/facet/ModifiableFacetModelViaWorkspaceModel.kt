// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.facet

import com.google.common.collect.HashBiMap
import com.intellij.facet.Facet
import com.intellij.facet.ModifiableFacetModel
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsImportedEntitySource
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule

internal class ModifiableFacetModelViaWorkspaceModel(private val initialStorage: TypedEntityStorage,
                                                     private val diff: TypedEntityStorageDiffBuilder,
                                                     legacyBridgeModule: LegacyBridgeModule,
                                                     private val facetManager: FacetManagerViaWorkspaceModel)
  : FacetModelViaWorkspaceModel(legacyBridgeModule), ModifiableFacetModel {
  private val listeners: MutableList<ModifiableFacetModel.Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  init {
    populateFrom(facetManager.model)
  }

  private fun getModuleEntity() = initialStorage.resolve(legacyBridgeModule.moduleEntityId)!!

  override fun addFacet(facet: Facet<*>) {
    addFacet(facet, null)
  }

  override fun addFacet(facet: Facet<*>, externalSource: ProjectModelExternalSource?) {
    val moduleEntity = getModuleEntity()
    val moduleSource = moduleEntity.entitySource
    val source = when {
      moduleSource is JpsFileEntitySource && externalSource != null ->
        JpsImportedEntitySource(moduleSource, externalSource.id, legacyBridgeModule.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource != null && moduleSource.externalSystemId != externalSource.id ->
        JpsImportedEntitySource(moduleSource.internalFile, externalSource.id, legacyBridgeModule.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource == null ->
        moduleSource.internalFile
      else -> moduleSource
    }
    val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
    val underlyingEntity = facet.underlyingFacet?.let { entityToFacet.inverse()[it]!! }
    val entity = diff.addFacetEntity(facet.name, facet.type.stringId, facetConfigurationXml, moduleEntity, underlyingEntity, source)
    entityToFacet[entity] = facet
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
    val moduleDiff = legacyBridgeModule.diff
    if (moduleDiff != null) {
      val res = moduleDiff.addDiff(diff)
      populateModel(res)
    }
    else {
      WorkspaceModel.getInstance(legacyBridgeModule.project).updateProjectModel {
        val res = it.addDiff(diff)
        populateModel(res)
      }
    }
  }

  private fun populateModel(replaceMap: Map<TypedEntity, TypedEntity>) {
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