package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface EntityWithChildren : WorkspaceEntity {
  val name: String
  val propertyChild: @Child ChildEntityType1?
  @Child val typeChild: ChildEntityType2?
}

interface ChildEntityType1 : WorkspaceEntity {
  val version: Int
  val parent: EntityWithChildren
}

interface ChildEntityType2 : WorkspaceEntity {
  val version: Int
  val parent: EntityWithChildren
}
