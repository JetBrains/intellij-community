// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.google.common.collect.HashBiMap
import com.intellij.facet.*
import com.intellij.facet.impl.FacetModelBase
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.facets
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.toExternalSource
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.jps.model.serialization.facet.FacetState

class FacetManagerBridge(module: Module) : FacetManagerBase() {
  internal val module = module as ModuleBridge
  internal val model = FacetModelBridge(this.module)

  private fun isThisModule(moduleEntity: ModuleEntity) = moduleEntity.name == module.name

  override fun checkConsistency() {
    model.checkConsistency(module.entityStorage.current.entities(FacetEntity::class.java).filter { isThisModule(it.module) }.toList())
  }

  override fun facetConfigurationChanged(facet: Facet<*>) {
    val facetEntity = model.getEntity(facet)
    if (facetEntity != null) {
      val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
      if (facetConfigurationXml != facetEntity.configurationXmlTag) {
        runWriteAction {
          val change: ModifiableFacetEntity.() -> Unit = { this.configurationXmlTag = facetConfigurationXml }
          module.diff?.modifyEntity(ModifiableFacetEntity::class.java, facetEntity, change) ?: WorkspaceModel.getInstance(module.project)
            .updateProjectModel { it.modifyEntity(ModifiableFacetEntity::class.java, facetEntity, change) }
        }
      }
    }
    super.facetConfigurationChanged(facet)
  }

  override fun getModel(): FacetModel = model
  override fun getModule(): Module = module
  override fun createModifiableModel(): ModifiableFacetModel {
    val diff = WorkspaceEntityStorageDiffBuilder.create(module.entityStorage.current)
    return createModifiableModel(diff)
  }

  fun createModifiableModel(diff: WorkspaceEntityStorageDiffBuilder): ModifiableFacetModel {
    return ModifiableFacetModelBridge(module.entityStorage.current, diff, module, this)
  }

}

internal open class FacetModelBridge(protected val moduleBridge: ModuleBridge) : FacetModelBase() {
  protected val entityToFacet: HashBiMap<FacetEntity, Facet<*>> = HashBiMap.create()

  override fun getAllFacets(): Array<Facet<*>> {
    return entityToFacet.values.toTypedArray()
  }

  internal fun getOrCreateFacet(entity: FacetEntity): Facet<*> {
    return entityToFacet.getOrPut(entity) { createFacet(entity) }
  }

  internal fun getFacet(entity: FacetEntity): Facet<*>? = entityToFacet[entity]

  internal fun getEntity(facet: Facet<*>): FacetEntity? = entityToFacet.inverse()[facet]

  private fun createFacet(entity: FacetEntity): Facet<*> {
    val registry = FacetTypeRegistry.getInstance()
    val facetType = registry.findFacetType(entity.facetType)
    val underlyingFacet = entity.underlyingFacet?.let { getOrCreateFacet(it) }
    if (facetType == null) {
      return FacetManagerBase.createInvalidFacet(moduleBridge, FacetState().apply {
        name = entity.name
        setFacetType(entity.facetType)
        configuration = entity.configurationXmlTag?.let { JDOMUtil.load(it) }
      }, underlyingFacet, ProjectBundle.message("error.message.unknown.facet.type.0", entity.facetType), true, true)
    }

    val configuration = facetType.createDefaultConfiguration()
    val configurationXmlTag = entity.configurationXmlTag
    if (configurationXmlTag != null) {
      FacetUtil.loadFacetConfiguration(configuration, JDOMUtil.load(configurationXmlTag))
    }
    val facet = facetType.createFacet(moduleBridge, entity.name, configuration, underlyingFacet)
    FacetManagerImpl.setExternalSource(facet, (entity.entitySource as? JpsImportedEntitySource)?.toExternalSource())
    return facet
  }

  fun populateFrom(mapping: HashBiMap<FacetEntity, Facet<*>>) {
    entityToFacet.putAll(mapping)
    facetsChanged()
  }

  internal fun populateFrom(mapping: FacetModelBridge) {
    entityToFacet.putAll(mapping.entityToFacet)
    facetsChanged()
  }

  fun removeEntity(entity: FacetEntity): Facet<*>? {
    val removed = entityToFacet.remove(entity)
    facetsChanged()
    return removed
  }

  fun updateEntity(oldEntity: FacetEntity, newEntity: FacetEntity): Facet<*>? {
    val oldFacet = entityToFacet.remove(oldEntity)
    if (oldFacet != null) {
      entityToFacet[newEntity] = oldFacet
    }
    facetsChanged()
    return entityToFacet[newEntity]
  }

  fun checkConsistency(facetEntities: List<FacetEntity>) {
    val facetEntitiesSet = facetEntities.toSet()
    for (entity in facetEntities) {
      val facet = entityToFacet[entity]
      if (facet == null) {
        throw IllegalStateException("No facet registered for $entity (name = ${entity.name})")
      }
      if (facet.name != entity.name) {
        throw IllegalStateException("Different name")
      }
      val entityFromMapping = entityToFacet.inverse()[facet]!!
      val facetsFromStorage = entityFromMapping.module.facets.toSet()
      if (facetsFromStorage != facetEntitiesSet) {
        throw IllegalStateException("Different set of facets from $entity storage: expected $facetEntitiesSet but was $facetsFromStorage")
      }
    }
    val staleEntity = (entityToFacet.keys - facetEntities).firstOrNull()
    if (staleEntity != null) {
      throw IllegalStateException("Stale entity $staleEntity (name = ${staleEntity.name}) in the mapping")
    }
  }
}
