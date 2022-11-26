// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jdom.Element
import org.jetbrains.jps.model.serialization.facet.FacetState

class DefaultFacetEntitySerializer: CustomFacetRelatedEntitySerializer<FacetEntity> {
  override val rootEntityType: Class<FacetEntity>
    get() = FacetEntity::class.java
  override val supportedFacetType: String
    get() = ""

  override fun loadEntitiesFromFacetState(builder: MutableEntityStorage, moduleEntity: ModuleEntity, facetState: FacetState,
                                          evaluateExternalSystemIdAndEntitySource: (FacetState) -> Pair<String?, EntitySource>) {
    loadFacetEntities(builder, moduleEntity, listOf(facetState), null, evaluateExternalSystemIdAndEntitySource)
  }

  private fun loadFacetEntities(builder: MutableEntityStorage, moduleEntity: ModuleEntity, facetStates: List<FacetState>, underlyingFacet: FacetEntity?,
                                evaluateExternalSystemIdAndEntitySource: (FacetState) -> Pair<String?, EntitySource>) {
    facetStates.forEach { facetState ->
      val (externalSystemId, entitySource) = evaluateExternalSystemIdAndEntitySource(facetState)
      val configurationXmlTag = facetState.configuration?.let { JDOMUtil.write(it) }

      // Check for existing facet it's needed in cases when we read sub-facet located in .xml but underling facet is from .iml,
      // thus same root facet will be declared in two places
      val newFacetId = FacetId(facetState.name, facetState.facetType, moduleEntity.symbolicId)
      var facetEntity: FacetEntity? = null
      val existingFacet = builder.resolve(newFacetId)
      if (existingFacet != null && configurationXmlTag != null) {
        if (existingFacet.configurationXmlTag == null) {
          facetEntity = builder.modifyEntity(existingFacet) {
            this.entitySource = entitySource
            this.configurationXmlTag = configurationXmlTag
          }
        }
      }

      if (existingFacet == null) {
        facetEntity = builder.addFacetEntity(facetState.name, facetState.facetType, configurationXmlTag, moduleEntity, underlyingFacet, entitySource)
      }

      if (facetEntity != null && externalSystemId != null) {
        builder.addEntity(FacetExternalSystemIdEntity(externalSystemId, entitySource) {
          this.facet = facetEntity
        })
      }
      loadFacetEntities(builder, moduleEntity, facetState.subFacets, facetEntity, evaluateExternalSystemIdAndEntitySource)
    }
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
      if (storeExternally) {
        externalSystemId = (facetEntity.entitySource as? JpsImportedEntitySource)?.externalSystemId
      }
      else {
        externalSystemIdInInternalStorage = facetEntity.facetExternalSystemIdEntity?.externalSystemId
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

  companion object {
    val instance: DefaultFacetEntitySerializer
      // It should be rewritten to `findExtensionOrFail` because of caching evaluation result
      get() = CustomFacetRelatedEntitySerializer.EP_NAME.extensions.filterIsInstance<DefaultFacetEntitySerializer>().first()

  }
}