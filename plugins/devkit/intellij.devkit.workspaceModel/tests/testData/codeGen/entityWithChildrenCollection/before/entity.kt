package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface ChildrenCollectionFieldEntity : WorkspaceEntity {
  val name: String
  val childrenEntitiesCollection: List<@Child SimpleEntity>
}

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  val parent: ChildrenCollectionFieldEntity
}
