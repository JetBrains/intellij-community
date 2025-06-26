package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface EntityWithChildren : WorkspaceEntity {
  val name: String
  val propertyChild: ChildEntityType1?
  val typeChild: ChildEntityType2?
}

interface ChildEntityType1 : WorkspaceEntity {
  val version: Int
  @Parent val parent: EntityWithChildren
}

interface ChildEntityType2 : WorkspaceEntity {
  val version: Int
  @Parent val parent: EntityWithChildren
}
