// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.FacetsOrderEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import com.intellij.platform.workspace.jps.entities.facetOrder
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState

internal class FacetsSerializer(
  private val internalSource: JpsFileEntitySource,
  private val externalStorage: Boolean,
  private val context: SerializationContext,
) {
  /**
   * This function should return void (Unit)
   * The current result value is a temporal solution to find the root cause of https://ea.jetbrains.com/browser/ea_problems/239676
   */
  internal fun loadFacetEntities(moduleEntity: ModuleEntity.Builder, facetManagerTag: Element) {
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    loadFacetEntities(facetManagerState.facets, moduleEntity, orderOfFacets)
    if (orderOfFacets.size > 1 && !externalStorage) {
      val entity = moduleEntity.facetOrder
      if (entity != null) {
        entity.orderOfFacets = orderOfFacets
      }
      else {
        FacetsOrderEntity(orderOfFacets, internalSource) {
          this.moduleEntity = moduleEntity
        }
      }
    }
  }

  private fun loadFacetEntities(facetStates: List<FacetState>, moduleEntity: ModuleEntity.Builder, orderOfFacets: MutableList<String>) {

    fun evaluateEntitySource(facetState: FacetState): EntitySource {
      val externalSystemId = facetState.externalSystemId ?: facetState.externalSystemIdInInternalStorage
      return if (externalSystemId == null) internalSource else JpsImportedEntitySource(internalSource, externalSystemId, externalStorage)
    }

    val facetTypeToSerializer = context.customFacetRelatedEntitySerializers.associateBy { it.supportedFacetType }
    for (facetState in facetStates) {
      orderOfFacets.add(facetState.name)
      val serializer = facetTypeToSerializer[facetState.facetType] ?: facetTypeToSerializer.getValue(
        DefaultFacetEntitySerializer.ALL_FACETS_TYPES_MARKER)
      serializer.loadEntitiesFromFacetState(moduleEntity, facetState, ::evaluateEntitySource)
    }
  }

  internal fun saveFacetEntities(moduleEntity: ModuleEntity?,
                                 affectedEntities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                 entitySourceFilter: (EntitySource) -> Boolean): Element? {
    val facetStatesFromEP = context.customFacetRelatedEntitySerializers
      .mapNotNull { entitySerializer ->
        val entitiesToSave = affectedEntities[entitySerializer.rootEntityType]?.filter { entitySourceFilter.invoke(it.entitySource) }
                             ?: return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        (entitySerializer as CustomFacetRelatedEntitySerializer<ModuleSettingsFacetBridgeEntity>).createFacetStateFromEntities(entitiesToSave.map { it as ModuleSettingsFacetBridgeEntity }, externalStorage)
      }.flatten()

    if (facetStatesFromEP.isEmpty()) {
      return null
    }

    val facetManagerState = FacetManagerState()
    val facetNameToFacetState = facetStatesFromEP.groupByTo(HashMap()) { it.name }
    val orderOfFacets = moduleEntity?.facetOrder?.orderOfFacets ?: emptyList()
    for (facetName in orderOfFacets) {
      facetNameToFacetState.remove(facetName)?.forEach {
        facetManagerState.facets.add(it)
      }
    }
    facetManagerState.facets.addAll(facetNameToFacetState.values.flatten())

    val componentTag = JDomSerializationUtil.createComponentElement("facets-root")
    XmlSerializer.serializeInto(facetManagerState, componentTag)
    return componentTag
  }
}
