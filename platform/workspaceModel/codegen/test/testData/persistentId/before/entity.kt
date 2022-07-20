package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.WorkspaceEntity

interface SimplePersistentIdEntity : WorkspaceEntityWithPersistentId {
  val version: Int
  val name: String
  val related: SimpleId

  override val persistentId: SimpleId
    get() = SimpleId(name)
}

data class SimpleId(val name: String) : PersistentEntityId<SimplePersistentIdEntity> {
  override val presentableName: String
    get() = name
}