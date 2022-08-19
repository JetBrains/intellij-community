// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.facet.FacetState

/**
 * The goal of this extension point is supporting serialization/deserialization of custom entities
 * related to the module and located at the same .iml file
 */
@ApiStatus.Internal
interface CustomModuleRelatedEntitySerializer {
  fun loadEntities(builder: MutableEntityStorage, moduleEntity: ModuleEntity, reader: JpsFileContentReader, imlFileUrl: VirtualFileUrl)

  fun saveEntities(moduleEntity: ModuleEntity?, writer: JpsFileContentWriter, imlFileUrl: VirtualFileUrl): FacetState?
}