// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FacetId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addFacetEntity
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild
import com.intellij.workspaceModel.storage.impl.references.OneToOneChild
import com.intellij.workspaceModel.storage.referrers
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState

internal class FacetEntitiesSerializer(private val imlFileUrl: VirtualFileUrl,
                                       private val internalSource: JpsFileEntitySource,
                                       private val componentName: String,
                                       private val externalStorage: Boolean) {
  /**
   * This function should return void (Unit)
   * The current result value is a temporal solution to find the root cause of https://ea.jetbrains.com/browser/ea_problems/239676
   */
  internal fun loadFacetEntities(builder: WorkspaceEntityStorageBuilder, moduleEntity: ModuleEntity, reader: JpsFileContentReader): Boolean {
    val facetManagerTag = reader.loadComponent(imlFileUrl.url, componentName) ?: return true
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    val res = loadFacetEntities(facetManagerState.facets, builder, moduleEntity, null, orderOfFacets)
    if (orderOfFacets.size > 1) {
      val entity = moduleEntity.facetsOrderEntity
      if (entity != null) {
        builder.modifyEntity(ModifiableFacetsOrderEntity::class.java, entity) {
          this.orderOfFacets = orderOfFacets
        }
      }
      else {
        builder.addEntity(ModifiableFacetsOrderEntity::class.java, internalSource) {
          module = moduleEntity
          this.orderOfFacets = orderOfFacets
        }
      }
    }
    return res
  }

  private fun loadFacetEntities(facetStates: List<FacetState>, builder: WorkspaceEntityStorageBuilder, moduleEntity: ModuleEntity,
                                underlyingFacet: FacetEntity?, orderOfFacets: MutableList<String>): Boolean {
    var res = true
    for (facetState in facetStates) {
      orderOfFacets.add(facetState.name)
      val configurationXmlTag = facetState.configuration?.let { JDOMUtil.write(it) }
      val externalSystemId = facetState.externalSystemId
      val source = if (externalSystemId == null) internalSource else JpsImportedEntitySource(internalSource, externalSystemId, externalStorage)

      // Check for existing facet
      val newFacetId = FacetId(facetState.name, facetState.facetType, moduleEntity.persistentId())
      if (builder.resolve(newFacetId) != null) {
        res = false
      }

      val facetEntity = builder.addFacetEntity(facetState.name, facetState.facetType, configurationXmlTag, moduleEntity, underlyingFacet,
                                               source)
      res = res && loadFacetEntities(facetState.subFacets, builder, moduleEntity, facetEntity, orderOfFacets)
    }
    return res
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
    val orderOfFacets = facets.first().module.facetsOrderEntity?.orderOfFacets ?: emptyList()
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

/**
 * This entity stores order of facets in iml file. This is needed to ensure that facet tags are saved in the same order to avoid
 * unnecessary modifications of iml file.
 */

@Suppress("unused")
internal class FacetsOrderEntityData : WorkspaceEntityData<FacetsOrderEntity>() {
  lateinit var orderOfFacets: List<String>

  override fun createEntity(snapshot: WorkspaceEntityStorage): FacetsOrderEntity {
    return FacetsOrderEntity(orderOfFacets).also { addMetaData(it, snapshot) }
  }
}

internal class FacetsOrderEntity(
  val orderOfFacets: List<String>
) : WorkspaceEntityBase() {
  val module: ModuleEntity by OneToOneChild.NotNull(ModuleEntity::class.java, true)
}

internal class ModifiableFacetsOrderEntity : ModifiableWorkspaceEntityBase<FacetsOrderEntity>() {
  var orderOfFacets: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(FacetsOrderEntity::class.java, ModuleEntity::class.java, true)
}

private val ModuleEntity.facetsOrderEntity get() = referrers(FacetsOrderEntity::module).firstOrNull()

