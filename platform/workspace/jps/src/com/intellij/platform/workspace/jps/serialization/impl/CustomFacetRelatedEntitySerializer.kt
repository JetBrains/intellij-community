// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.storage.EntitySource
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.facet.FacetState
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer

/**
 * Originally, [com.intellij.facet.Facet] were made to support custom setting for the module, but this solution is not
 * rather flexible and thanks to the workspace model we have an opportunity to make custom entities describing additional
 * module settings. To have a sort of bridge between new approach with declaring custom module settings and [com.intellij.facet.Facet]
 * several extension points were introduced:
 *  1) [CustomFacetRelatedEntitySerializer] to add support custom entity
 *  serialization/deserialization as facet tag.
 *  2) [com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor] to have an option to fire different sorts of event related to the [com.intellij.facet.Facet] during
 *  the changes of your custom entity this extension point should be implemented.
 *
 * If you want to use your custom module setting entity under the hood of your facet you also need to implement
 * [com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetBridge] to be properly updated.
 *
 * **N.B. Most of the time you need to implement them all to have a correct support all functionality relying on Facets.**
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface CustomFacetRelatedEntitySerializer<T: ModuleSettingsBase> {
  /**
   * Declare class for the main entity associated with [com.intellij.facet.Facet].
   */
  val rootEntityType: Class<T>

  /**
   * Facet type [com.intellij.facet.FacetType.getStringId] this extension point can serialization/deserialization.
   * The result of deserialization, entities of the type declared at [rootEntityType] in the [com.intellij.platform.workspace.storage.EntityStorage]
   */
  val supportedFacetType: String

  /**
   * Method for deserialization [org.jetbrains.jps.model.serialization.facet.FacetState] read from external source. Facet state is
   * an intermediate representation to avoid core communication with tags directly.
   * @param moduleEntity module to which these settings belong
   * @param facetState intermediate representation of facet related data read out from external sources
   * @param evaluateEntitySource function which should be invoked to get [com.intellij.platform.workspace.storage.EntitySource]
   * for your entities and externalSystemId which should be stored somewhere in your entities
   */
  fun loadEntitiesFromFacetState(moduleEntity: ModuleEntity, facetState: FacetState, evaluateEntitySource: (FacetState) -> EntitySource)

  /**
   * Create intermediate representation from entities of declared at [rootEntityType] type which will be used for serialization on disk.
   * @param entities list of certain type entities
   * @param storeExternally indicator which tells where this data will be store, under `.idea` or in external system folder
   */
  fun createFacetStateFromEntities(entities: List<T>, storeExternally: Boolean): List<FacetState> {
    return entities.map { settingsEntity ->
      val state = FacetState().apply {
        name = settingsEntity.name
        facetType = supportedFacetType
        configuration = Element(JpsFacetSerializer.CONFIGURATION_TAG)
        val externalSystemIdValue = (settingsEntity.entitySource as? JpsImportedEntitySource)?.externalSystemId
        if (storeExternally) {
          externalSystemId = externalSystemIdValue
        }
        else {
          externalSystemIdInInternalStorage = externalSystemIdValue
        }
      }
      state.configuration = serializeIntoXml(settingsEntity)
      return@map state
    }
  }

  /**
   * Method for creation facet XML tag from root type entity passed as a parameter
   * Default implementation creates "configuration" root element and calls [serialize] method
   */
  fun serializeIntoXml(entity: T): Element = serialize(entity, Element(JpsFacetSerializer.CONFIGURATION_TAG))

  /**
   * Method for creation facet XML tag from root type entity and root element passed as parameters
   */
  fun serialize(entity: T, rootElement: Element): Element
}