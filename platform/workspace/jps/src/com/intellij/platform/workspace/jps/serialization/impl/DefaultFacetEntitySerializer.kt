// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jdom.Element
import org.jetbrains.jps.model.serialization.facet.FacetState
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

private val facetEntityTypes = ConcurrentFactoryMap.createMap<String, FacetEntityTypeId> { FacetEntityTypeId(it) }

class DefaultFacetEntitySerializer: CustomFacetRelatedEntitySerializer<FacetEntity> {
  override val rootEntityType: Class<FacetEntity>
    get() = FacetEntity::class.java
  override val supportedFacetType: String
    get() = ALL_FACETS_TYPES_MARKER

  // On a large project we may get a lot of string duplicates from facets
  // On test with 50_000 modules such duplicates eat around 25MB of memory
  // This class is used in concurrent environment, so the map has to be concurrent.
  private val configurationStringInterner = ConcurrentHashMap<String, String>()

  override fun loadEntitiesFromFacetState(moduleEntity: ModuleEntity.Builder,
                                          facetState: FacetState,
                                          evaluateEntitySource: (FacetState) -> EntitySource) {
    loadFacetEntities(moduleEntity, listOf(facetState), null, evaluateEntitySource)
  }

  private fun loadFacetEntities(moduleEntity: ModuleEntity.Builder,
                                facetStates: List<FacetState>,
                                underlyingFacet: FacetEntity.Builder?,
                                evaluateEntitySource: (FacetState) -> EntitySource) {
    facetStates.forEach { facetState ->
      val entitySource = evaluateEntitySource(facetState)
      val configurationXmlTagRaw = facetState.configuration?.let { JDOMUtil.write(it) }
      val configurationXmlTag = configurationXmlTagRaw?.let { configurationStringInterner.computeIfAbsent(it, Function.identity()) }

      // Check for existing facet it's needed in cases when we read sub-facet located in .xml but underling facet is from .iml,
      // thus the same root facet will be declared in two places
      val facetEntityTypeId = facetEntityTypes[facetState.facetType]!!
      val newFacetId = FacetId(facetState.name, facetEntityTypeId, ModuleId(moduleEntity.name))
      var facetEntity: FacetEntity.Builder? = null
      val existingFacet = findFacetById(moduleEntity.facets, newFacetId)
      if (existingFacet != null && configurationXmlTag != null) {
        if (existingFacet.configurationXmlTag == null) {
          existingFacet.apply {
            this.entitySource = entitySource
            this.configurationXmlTag = configurationXmlTag
          }
          facetEntity = existingFacet
        }
      }

      if (existingFacet == null) {
        facetEntity = FacetEntity.invoke(ModuleId(moduleEntity.name), facetState.name, facetEntityTypeId, entitySource) {
          this.configurationXmlTag = configurationXmlTag
          this.module = moduleEntity
          this.underlyingFacet = underlyingFacet
        }
      }

      loadFacetEntities(moduleEntity, facetState.subFacets, facetEntity, evaluateEntitySource)
    }
  }

  private fun findFacetById(facets: List<FacetEntity.Builder>, id: FacetId): FacetEntity.Builder? {
    for (facet in facets) {
      if (FacetId(facet.name, facet.typeId, ModuleId(facet.module.name)) == id) return facet
      val subs = findFacetById(facet.childrenFacets, id)
      if (subs != null) return subs
    }
    return null
  }

  override fun createFacetStateFromEntities(entities: List<FacetEntity>, storeExternally: Boolean): List<FacetState> {
    val existingFacetStates = HashMap<String, FacetState>()
    val resultFacetStates = ArrayList<FacetState>()
     entities.forEach { facetEntity ->
      val state = getOrCreateFacetState(facetEntity, existingFacetStates, resultFacetStates, storeExternally)
      // Serializing configuration of sub-facet but not a root facet e.g. if it's from .iml file but sub-facet from .xml
      state.configuration = facetEntity.configurationXmlTag?.let { JDOMUtil.load(it) }
    }
    return resultFacetStates
  }

  private fun getOrCreateFacetState(facetEntity: FacetEntity, existingFacetStates: MutableMap<String, FacetState>,
                                    rootFacets: MutableList<FacetState>, storeExternally: Boolean): FacetState {
    val existing = existingFacetStates[facetEntity.name]
    if (existing != null) return existing

    val state = FacetState().apply {
      name = facetEntity.name
      facetType = facetEntity.typeId.name
      val externalSystemIdValue = (facetEntity.entitySource as? JpsImportedEntitySource)?.externalSystemId
      if (storeExternally) {
        externalSystemId = externalSystemIdValue
      }
      else {
        externalSystemIdInInternalStorage = externalSystemIdValue
      }
    }
    existingFacetStates[state.name] = state
    val underlyingFacet = facetEntity.underlyingFacet
    if (underlyingFacet != null) {
      getOrCreateFacetState(underlyingFacet, existingFacetStates, rootFacets, storeExternally).subFacets.add(state)
    }
    else {
      rootFacets.add(state)
    }
    return state
  }

  override fun serializeIntoXml(entity: FacetEntity): Element {
    return entity.configurationXmlTag?.let { JDOMUtil.load(it) } ?: Element("configuration")
  }

  override fun serializeIntoXmlBuilder(entity: WorkspaceEntity.Builder<out FacetEntity>, module: ModuleEntity): Element {
    entity as FacetEntity.Builder
    return entity.configurationXmlTag?.let { JDOMUtil.load(it) } ?: Element("configuration")
  }

  override fun serialize(entity: FacetEntity, rootElement: Element): Element = error("Unsupported operation")
  override fun serializeBuilder(entity: WorkspaceEntity.Builder<out FacetEntity>, module: ModuleEntity, rootElement: Element): Element = error("Unsupported operation")

  companion object {
    internal const val ALL_FACETS_TYPES_MARKER = "<all types of facets>"
  }
}