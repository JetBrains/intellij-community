// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState
import com.intellij.workspaceModel.storage.bridgeEntities.api.modifyEntity

internal class FacetEntitiesSerializer(private val imlFileUrl: VirtualFileUrl,
                                       private val internalSource: JpsFileEntitySource,
                                       private val componentName: String,
                                       private val baseModuleDirPath: String?,
                                       private val externalStorage: Boolean) {
  /**
   * This function should return void (Unit)
   * The current result value is a temporal solution to find the root cause of https://ea.jetbrains.com/browser/ea_problems/239676
   */
  internal fun loadFacetEntities(builder: MutableEntityStorage, moduleEntity: ModuleEntity, reader: JpsFileContentReader) {
    val facetManagerTag = reader.loadComponent(imlFileUrl.url, componentName, baseModuleDirPath) ?: return
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    loadFacetEntities(facetManagerState.facets, builder, moduleEntity, null, orderOfFacets)
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
                                underlyingFacet: FacetEntity?, orderOfFacets: MutableList<String>) {
    for (facetState in facetStates) {
      orderOfFacets.add(facetState.name)
      val configurationXmlTag = facetState.configuration?.let { JDOMUtil.write(it) }
      val externalSystemId = facetState.externalSystemId ?: facetState.externalSystemIdInInternalStorage
      val source = if (externalSystemId == null) internalSource else JpsImportedEntitySource(internalSource, externalSystemId, externalStorage)

      // Check for existing facet
      val newFacetId = FacetId(facetState.name, facetState.facetType, moduleEntity.persistentId)
      var facetEntity: FacetEntity? = null
      val existingFacet = builder.resolve(newFacetId)
      if (existingFacet != null && configurationXmlTag != null) {
        if (existingFacet.configurationXmlTag == null) {
          facetEntity = builder.modifyEntity(existingFacet)  { this.configurationXmlTag = configurationXmlTag }
          facetEntity = builder.modifyEntity(facetEntity) { this.entitySource = source }
        }
      }

      if (existingFacet == null) {
        facetEntity = builder.addFacetEntity(facetState.name, facetState.facetType, configurationXmlTag, moduleEntity, underlyingFacet, source)
      }

      if (facetEntity != null && externalSystemId != null && !externalStorage) {
        builder.addEntity(FacetExternalSystemIdEntity(externalSystemId, source) {
          this.facet = facetEntity
        })
      }
      loadFacetEntities(facetState.subFacets, builder, moduleEntity, facetEntity, orderOfFacets)
    }
  }

  internal fun saveFacetEntities(facets: List<FacetEntity>, writer: JpsFileContentWriter) {
    val fileUrl = imlFileUrl.url
    if (facets.isEmpty()) {
      writer.saveComponent(fileUrl, componentName, null)
      return
    }

    val facetManagerState = FacetManagerState()
    val facetStates = HashMap<String, FacetState>()
    val facetsByName = facets.groupByTo(HashMap()) { it.name }
    val orderOfFacets = facets.first().module.facetOrder?.orderOfFacets ?: emptyList()
    for (facetName in orderOfFacets) {
      facetsByName.remove(facetName)?.forEach {
        saveFacet(it, facetStates, facetManagerState.facets)
      }
    }
    facetsByName.values.forEach {
      it.forEach {
        saveFacet(it, facetStates, facetManagerState.facets)
      }
    }
    val componentTag = JDomSerializationUtil.createComponentElement(componentName)
    XmlSerializer.serializeInto(facetManagerState, componentTag)
    if (externalStorage && FileUtil.extensionEquals(fileUrl, "iml")) {
      // Trying to catch https://ea.jetbrains.com/browser/ea_problems/239676
      logger<FacetEntitiesSerializer>().error("""Incorrect file for the serializer
        |externalStorage: $externalStorage
        |file path: $fileUrl
        |componentName: $componentName
      """.trimMargin())
    }
    writer.saveComponent(fileUrl, componentName, componentTag)
  }

  private fun saveFacet(facetEntity: FacetEntity, facetStates: MutableMap<String, FacetState>, rootFacets: MutableList<FacetState>) {
    val state = getOrCreateFacetState(facetEntity, facetStates, rootFacets)
    state.configuration = facetEntity.configurationXmlTag?.let { JDOMUtil.load(it) }

  }

  private fun getOrCreateFacetState(facetEntity: FacetEntity, facetStates: MutableMap<String, FacetState>, rootFacets: MutableList<FacetState>): FacetState {
    val existing = facetStates[facetEntity.name]
    if (existing != null) return existing

    val state = FacetState().apply {
      name = facetEntity.name
      facetType = facetEntity.facetType
      if (externalStorage) {
        externalSystemId = (facetEntity.entitySource as? JpsImportedEntitySource)?.externalSystemId
      }
      else {
        externalSystemIdInInternalStorage = facetEntity.facetExternalSystemIdEntity?.externalSystemId
      }
    }
    facetStates[state.name] = state
    val underlyingFacet = facetEntity.underlyingFacet
    val targetList =
      if (underlyingFacet != null) getOrCreateFacetState(underlyingFacet, facetStates, rootFacets).subFacets
      else rootFacets
    targetList += state
    return state
  }

}
