// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

interface TreeMultiparentRootEntity : WorkspaceEntityWithSymbolicId {
  val data: String

  val children: List<TreeMultiparentLeafEntity>

  override val symbolicId: TreeMultiparentSymbolicId
    get() = TreeMultiparentSymbolicId(data)
}

interface TreeMultiparentLeafEntity : WorkspaceEntity {
  val data: String

  @Parent
  val mainParent: TreeMultiparentRootEntity?
  @Parent
  val leafParent: TreeMultiparentLeafEntity?
  val children: List<TreeMultiparentLeafEntity>
}

data class TreeMultiparentSymbolicId(val data: String) : SymbolicEntityId<TreeMultiparentRootEntity> {
  override val presentableName: String
    get() = data
}