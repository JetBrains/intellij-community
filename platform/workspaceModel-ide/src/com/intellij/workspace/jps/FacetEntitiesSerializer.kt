// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.EntityDataDelegation
import com.intellij.workspace.api.pstorage.PEntityData
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import com.intellij.workspace.api.pstorage.references.MutableOneToOneChild
import com.intellij.workspace.api.pstorage.references.OneToOneChild
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsImportedEntitySource
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState

internal class FacetEntitiesSerializer(private val imlFileUrl: VirtualFileUrl,
                                       private val internalSource: JpsFileEntitySource,
                                       private val componentName: String,
                                       private val externalStorage: Boolean) {
  internal fun loadFacetEntities(builder: TypedEntityStorageBuilder, moduleEntity: ModuleEntity, reader: JpsFileContentReader) {
    val facetManagerTag = reader.loadComponent(imlFileUrl.url, componentName) ?: return
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    loadFacetEntities(facetManagerState.facets, builder, moduleEntity, null, orderOfFacets)
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
  }

  private fun loadFacetEntities(facetStates: List<FacetState>, builder: TypedEntityStorageBuilder, moduleEntity: ModuleEntity,
                                underlyingFacet: FacetEntity?, orderOfFacets: MutableList<String>) {
    for (facetState in facetStates) {
      orderOfFacets.add(facetState.name)
      val configurationXmlTag = facetState.configuration?.let { JDOMUtil.write(it) }
      val externalSystemId = facetState.externalSystemId
      val source = if (externalSystemId == null) internalSource else JpsImportedEntitySource(internalSource, externalSystemId, externalStorage)
      val facetEntity = builder.addFacetEntity(facetState.name, facetState.facetType, configurationXmlTag, moduleEntity, underlyingFacet,
                                               source)
      loadFacetEntities(facetState.subFacets, builder, moduleEntity, facetEntity, orderOfFacets)
    }
  }

  internal fun saveFacetEntities(facets: List<FacetEntity>, writer: JpsFileContentWriter) {
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
    writer.saveComponent(imlFileUrl.url, componentName, componentTag)
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
internal class FacetsOrderEntityData : PEntityData<FacetsOrderEntity>() {
  lateinit var orderOfFacets: List<String>

  override fun createEntity(snapshot: TypedEntityStorage): FacetsOrderEntity {
    return FacetsOrderEntity(orderOfFacets.toList()).also { addMetaData(it, snapshot) }
  }
}

internal class FacetsOrderEntity(
  val orderOfFacets: List<String>
) : PTypedEntity() {
  val module: ModuleEntity by OneToOneChild.NotNull(ModuleEntity::class, true)
}

internal class ModifiableFacetsOrderEntity : PModifiableTypedEntity<FacetsOrderEntity>() {
  var orderOfFacets: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(FacetsOrderEntity::class, ModuleEntity::class, true)
}

private val ModuleEntity.facetsOrderEntity get() = referrers(FacetsOrderEntity::module).firstOrNull()

