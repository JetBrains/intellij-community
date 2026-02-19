package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface EntityWithSelfRef : WorkspaceEntity {
  val name: String
  @Parent
  val parentRef: EntityWithSelfRef?
  val children: List<EntityWithSelfRef>
}
