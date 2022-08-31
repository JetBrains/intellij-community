// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.facet.FacetState

@ApiStatus.Internal
interface CustomFacetEntitySerializer<T> {
  val entityType: Class<T>
  fun serializeIntoXml(entity: T): Element

  companion object {
    val EP_NAME: ExtensionPointName<CustomFacetEntitySerializer<WorkspaceEntity>> = ExtensionPointName.create("com.intellij.workspaceModel.customFacetEntitySerializer")
  }
}