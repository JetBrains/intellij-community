// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

interface ModuleTestEntity : WorkspaceEntityWithSymbolicId {
  val name: String

  val contentRoots: List<ContentRootTestEntity>
  val facets: List<FacetTestEntity>

  override val symbolicId: ModuleTestEntitySymbolicId
    get() = ModuleTestEntitySymbolicId(name)
}

interface ContentRootTestEntity : WorkspaceEntity {
  @Parent
  val module: ModuleTestEntity
  val sourceRootOrder: SourceRootTestOrderEntity?
  val sourceRoots: List<SourceRootTestEntity>
}

interface SourceRootTestOrderEntity : WorkspaceEntity {
  val data: String
  @Parent
  val contentRoot: ContentRootTestEntity
}

interface SourceRootTestEntity : WorkspaceEntity {
  val data: String
  @Parent
  val contentRoot: ContentRootTestEntity
}

data class ModuleTestEntitySymbolicId(val name: String) : SymbolicEntityId<ModuleTestEntity> {
  override val presentableName: String
    get() = name
}

data class FacetTestEntitySymbolicId(val name: String) : SymbolicEntityId<FacetTestEntity> {
  override val presentableName: String
    get() = name
}

interface FacetTestEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  val moreData: String
  @Parent
  val module: ModuleTestEntity

  override val symbolicId: FacetTestEntitySymbolicId
    get() = FacetTestEntitySymbolicId(data)
}
