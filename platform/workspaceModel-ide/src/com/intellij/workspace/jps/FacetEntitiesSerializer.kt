// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.facet.FacetManagerState
import org.jetbrains.jps.model.serialization.facet.FacetState
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer

internal class FacetEntitiesSerializer(private val imlFileUrl: VirtualFileUrl,
                                       private val source: JpsFileEntitySource) {
  internal fun loadFacetEntities(builder: TypedEntityStorageBuilder, moduleEntity: ModuleEntity, reader: JpsFileContentReader) {
    val facetManagerTag = reader.loadComponent(imlFileUrl.url, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME) ?: return
    val facetManagerState = XmlSerializer.deserialize(facetManagerTag, FacetManagerState::class.java)
    val orderOfFacets = ArrayList<String>()
    loadFacetEntities(facetManagerState.facets, builder, moduleEntity, null, orderOfFacets)
    if (orderOfFacets.size > 1) {
      val entity = moduleEntity.facetsOrderEntity
      if (entity != null) {
        builder.modifyEntity(FacetsOrderEntity::class.java, entity) {
          this.orderOfFacets = orderOfFacets
        }
      }
      else {
        builder.addEntity(FacetsOrderEntity::class.java, source) {
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
      val facetEntity = builder.addFacetEntity(facetState.name, facetState.facetType, configurationXmlTag, moduleEntity, underlyingFacet, source)
      loadFacetEntities(facetState.subFacets, builder, moduleEntity, facetEntity, orderOfFacets)
    }
  }

  internal fun saveFacetEntities(module: ModuleEntity, facets: List<FacetEntity>, writer: JpsFileContentWriter) {
    val facetManagerState = FacetManagerState()
    val facetStates = HashMap<String, FacetState>()
    val facetsByName = facets.groupByTo(HashMap()) { it.name }
    val orderOfFacets = module.facetsOrderEntity?.orderOfFacets ?: emptyList()
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
    val componentTag = JDomSerializationUtil.createComponentElement(JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME)
    XmlSerializer.serializeInto(facetManagerState, componentTag)
    writer.saveComponent(imlFileUrl.url, JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME, componentTag)
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
internal interface FacetsOrderEntity : ModifiableTypedEntity<FacetsOrderEntity> {
  var orderOfFacets: List<String>
  var module: ModuleEntity
}

private val ModuleEntity.facetsOrderEntity get() = referrers(FacetsOrderEntity::module).firstOrNull()

