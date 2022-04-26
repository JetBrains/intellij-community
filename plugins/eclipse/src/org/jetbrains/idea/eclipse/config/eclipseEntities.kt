// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.workspaceModel.ide.JpsFileDependentEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.EclipseProjectPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

data class EclipseProjectFile(
  val classpathFile: VirtualFileUrl,
  val internalSource: JpsFileEntitySource
) : EntitySource, JpsFileDependentEntitySource {
  override val originalSource: JpsFileEntitySource
    get() = internalSource
}

fun MutableEntityStorage.addEclipseProjectPropertiesEntity(module: ModuleEntity, source: EntitySource): EclipseProjectPropertiesEntity {
  val entity = EclipseProjectPropertiesEntity(source, LinkedHashMap(), ArrayList(), ArrayList(), ArrayList(), false, 0, LinkedHashMap()) {
    this.module = module
}
  this.addEntity(entity)
  return entity
}


fun EclipseProjectPropertiesEntity.Builder.setVariable(kind: String, name: String, path: String) {
  variablePaths = variablePaths.toMutableMap().also { it[kind + path] = name }
}

fun EclipseProjectPropertiesEntity.getVariable(kind: String, path: String): String? = variablePaths[kind + path]