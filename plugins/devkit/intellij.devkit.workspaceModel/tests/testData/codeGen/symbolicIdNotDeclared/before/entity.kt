package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.PersistentEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface SimpleSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val version: Int
  val name: String
}

data class SimpleId(val name: String) : SymbolicEntityId<SimpleSymbolicIdEntity> {
  override val presentableName: String
    get() = name
}