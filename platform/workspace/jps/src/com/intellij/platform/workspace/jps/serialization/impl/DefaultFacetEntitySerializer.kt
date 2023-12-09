// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.childrenFacets
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.util.containers.Interner
import org.jdom.Element
import org.jetbrains.jps.model.serialization.facet.FacetState

class DefaultFacetEntitySerializer: CustomFacetRelatedEntitySerializer<FacetEntity> {
  override val rootEntityType: Class<FacetEntity>
    get() = FacetEntity::class.java
  override val supportedFacetType: String
    get() = ALL_FACETS_TYPES_MARKER

  // On large project we may get a lot of string duplicates from facets
  // On test with 50_000 modules such duplicates eat around 25MB of memory
  private val configurationStringInterner = Interner.createStringInterner()

  override fun loadEntitiesFromFacetState(moduleEntity: ModuleEntity,
                                          facetState: FacetState,
                                          evaluateEntitySource: (FacetState) -> EntitySource) {
    loadFacetEntities(moduleEntity, listOf(facetState), null, evaluateEntitySource)
  }

  private fun loadFacetEntities(moduleEntity: ModuleEntity,
                                facetStates: List<FacetState>,
                                underlyingFacet: FacetEntity?,
                                evaluateEntitySource: (FacetState) -> EntitySource) {
    facetStates.forEach { facetState ->
      val entitySource = evaluateEntitySource(facetState)
      val configurationXmlTagRaw = facetState.configuration?.let { JDOMUtil.write(it) }
      val configurationXmlTag = configurationXmlTagRaw?.let { configurationStringInterner.intern(it) }

      // Check for existing facet it's needed in cases when we read sub-facet located in .xml but underling facet is from .iml,
      // thus same root facet will be declared in two places
      val newFacetId = FacetId(facetState.name, facetState.facetType, moduleEntity.symbolicId)
      var facetEntity: FacetEntity? = null
      val existingFacet = findFacetById(moduleEntity.facets, newFacetId)
      if (existingFacet != null && configurationXmlTag != null) {
        if (existingFacet.configurationXmlTag == null) {
          (existingFacet as FacetEntity.Builder).apply {
            this.entitySource = entitySource
            this.configurationXmlTag = configurationXmlTag
          }
          facetEntity = existingFacet
        }
      }

      if (existingFacet == null) {
        facetEntity = FacetEntity(facetState.name, moduleEntity.symbolicId, facetState.facetType, entitySource) {
          this.configurationXmlTag = configurationXmlTag
          this.module = moduleEntity
          this.underlyingFacet = underlyingFacet
        }
      }

      loadFacetEntities(moduleEntity, facetState.subFacets, facetEntity, evaluateEntitySource)
    }
  }

  private fun findFacetById(facets: List<FacetEntity>, id: FacetId): FacetEntity? {
    for (facet in facets) {
      if (facet.symbolicId == id) return facet
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
      facetType = facetEntity.facetType
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

  override fun serialize(entity: FacetEntity, rootElement: Element): Element = error("Unsupported operation")

  companion object {
    internal const val ALL_FACETS_TYPES_MARKER = "<all types of facets>"
  }
}