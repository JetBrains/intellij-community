package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase

data class SimpleSymbolicId(val name: String) : SymbolicEntityId<SimpleEntity> {
  override val presentableName: String
    get() = "Name: $name"
}

@OptIn(WorkspaceEntityInternalApi::class)
interface SimpleEntity : WorkspaceEntityWithSymbolicId {
  val name: String
  val relatedId: SimpleSymbolicId?
  val relatedEntityInDeclaration: SimpleEntity?
    get() {
      val thisAsBase = this as? WorkspaceEntityBase ?: return null
      val relatedId = this.relatedId ?: return null
      return thisAsBase.snapshot.resolve(relatedId)
    }
  override val symbolicId: SimpleSymbolicId
    get() = SimpleSymbolicId(name)
}

@OptIn(WorkspaceEntityInternalApi::class)
val SimpleEntity.relatedEntity: SimpleEntity?
  get() {
    val thisAsBase = this as? WorkspaceEntityBase ?: return null
    val relatedId = this.relatedId ?: return null
    return thisAsBase.snapshot.resolve(relatedId)
  }
  
  