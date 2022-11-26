// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity

internal class FacetsSerializer(private val imlFileUrl: VirtualFileUrl, private val internalSource: JpsFileEntitySource,
                                private val componentName: String, private val baseModuleDirPath: String?, private val externalStorage: Boolean) {
  /**
   * This function should return void (Unit)
   * The current result value is a temporal solution to find the root cause of https://ea.jetbrains.com/browser/ea_problems/239676
   */
  internal fun loadFacetEntities(builder: MutableEntityStorage, moduleEntity: ModuleEntity, reader: JpsFileContentReader) {
    val facetManagerTag = reader.loadComponent(imlFileUrl.url, componentName, baseModuleDirPath) ?: return
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    loadFacetEntities(facetManagerState.facets, builder, moduleEntity, orderOfFacets)
    if (orderOfFacets.size > 1 && !externalStorage) {
      val entity = moduleEntity.facetOrder
      if (entity != null) {
        builder.modifyEntity(entity) {
          this.orderOfFacets = orderOfFacets
        }
      }
      else {
        builder.addEntity(FacetsOrderEntity(orderOfFacets, internalSource) {
          this.moduleEntity = moduleEntity
        })
      }
    }
  }

  private fun loadFacetEntities(facetStates: List<FacetState>, builder: MutableEntityStorage, moduleEntity: ModuleEntity,
                                orderOfFacets: MutableList<String>) {

    fun evaluateExternalSystemIdAndEntitySource(facetState: FacetState): Pair<String?, EntitySource> {
      val externalSystemId = facetState.externalSystemId ?: facetState.externalSystemIdInInternalStorage
      val entitySource = if (externalSystemId == null) internalSource
      else JpsImportedEntitySource(internalSource, externalSystemId, externalStorage)
      val actualExternalSystemId = if (externalSystemId != null && !externalStorage) externalSystemId else null
      return actualExternalSystemId to entitySource
    }

    val facetTypeToSerializer = CustomFacetRelatedEntitySerializer.EP_NAME.extensionList.associateBy { it.supportedFacetType }
    for (facetState in facetStates) {
      orderOfFacets.add(facetState.name)
      val serializer = facetTypeToSerializer[facetState.facetType] ?: DefaultFacetEntitySerializer.instance
      serializer.loadEntitiesFromFacetState(builder, moduleEntity, facetState, ::evaluateExternalSystemIdAndEntitySource)
    }
  }

  internal fun saveFacetEntities(moduleEntity: ModuleEntity?,
                                 affectedEntities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                                 writer: JpsFileContentWriter,
                                 entitySourceFilter: (EntitySource) -> Boolean) {
    val fileUrl = imlFileUrl.url

    val facetStatesFromEP = CustomFacetRelatedEntitySerializer.EP_NAME.extensionList
      .mapNotNull { entitySerializer ->
        val entitiesToSave = affectedEntities[entitySerializer.rootEntityType]?.filter { entitySourceFilter.invoke(it.entitySource) }
                             ?: return@mapNotNull null
        entitySerializer.createFacetStateFromEntities(entitiesToSave, externalStorage)
      }.flatten()

    if (facetStatesFromEP.isEmpty()) {
      writer.saveComponent(fileUrl, componentName, null)
      return
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

    val componentTag = JDomSerializationUtil.createComponentElement(componentName)
    XmlSerializer.serializeInto(facetManagerState, componentTag)
    if (externalStorage && FileUtil.extensionEquals(fileUrl, "iml")) {
      // Trying to catch https://ea.jetbrains.com/browser/ea_problems/239676
      logger<FacetsSerializer>().error("""Incorrect file for the serializer
        |externalStorage: $externalStorage
        |file path: $fileUrl
        |componentName: $componentName
      """.trimMargin())
    }
    writer.saveComponent(fileUrl, componentName, componentTag)
  }
}
