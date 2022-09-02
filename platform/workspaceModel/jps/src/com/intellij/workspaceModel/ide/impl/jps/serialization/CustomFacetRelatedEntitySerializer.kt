// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.facet.FacetState

/**
 * The goal of this extension point is supporting serialization/deserialization of custom entities
 * related to the module and located at the same .iml file
 */
@ApiStatus.Internal
interface CustomFacetRelatedEntitySerializer<T: WorkspaceEntity> {
  val rootEntityType: Class<T>
  val supportedFacetType: String
  fun loadEntitiesFromFacetState(builder: MutableEntityStorage, moduleEntity: ModuleEntity, facetState: FacetState,
                                 evaluateExternalSystemIdAndEntitySource: (FacetState) -> Pair<String?, EntitySource>)
  fun createFacetStateFromEntities(entities: List<T>, storeExternally: Boolean): List<FacetState>
  fun serializeIntoXml(entity: T): Element

  companion object {
    val EP_NAME: ExtensionPointName<CustomFacetRelatedEntitySerializer<WorkspaceEntity>> =
      ExtensionPointName.create("com.intellij.workspaceModel.customFacetRelatedEntitySerializer")
  }
}