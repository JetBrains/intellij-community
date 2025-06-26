package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface ChildrenCollectionFieldEntity : WorkspaceEntity {
  val name: String
  val childrenEntitiesCollection: List<SimpleEntity>
}

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  @Parent val parent: ChildrenCollectionFieldEntity
}
