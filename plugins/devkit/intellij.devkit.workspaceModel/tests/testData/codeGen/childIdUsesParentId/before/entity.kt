package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

data class ParentId(val name: String) : SymbolicEntityId<ParentEntity> {
  override val presentableName: String
    get() = name
}

data class ChildId(val name: String, val parentId: ParentId) : SymbolicEntityId<ChildEntity> {
  override val presentableName: String
    get() = "$name (${parentId.presentableName})"
}

interface ParentEntity : WorkspaceEntityWithSymbolicId {
  val name: String
  override val symbolicId: ParentId
    get() = ParentId(name)
}

val ParentEntity.child: ChildEntity
  by WorkspaceEntity.extension()

interface ChildEntity : WorkspaceEntityWithSymbolicId {
  val name: String
  @Parent
  val parentEntity: ParentEntity
  override val symbolicId: ChildId
    get() = ChildId(name, parentEntity.symbolicId)
}