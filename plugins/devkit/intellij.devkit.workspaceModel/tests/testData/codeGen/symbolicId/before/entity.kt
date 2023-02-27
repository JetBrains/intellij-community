package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithSymbolicId

interface SimpleSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val version: Int
  val name: String
  val related: SimpleId
  val sealedClassWithLinks: SealedClassWithLinks

  override val symbolicId: SimpleId
    get() = SimpleId(name)
}

data class SimpleId(val name: String) : SymbolicEntityId<SimpleSymbolicIdEntity> {
  override val presentableName: String
    get() = name
}

sealed class SealedClassWithLinks {
  object Nothing : SealedClassWithLinks()
  data class Single(val id: SimpleId) : SealedClassWithLinks()

  sealed class Many() : SealedClassWithLinks() {
    data class Ordered(val list: List<SimpleId>) : Many()
    data class Unordered(val set: Set<SimpleId>) : Many()
  }

}