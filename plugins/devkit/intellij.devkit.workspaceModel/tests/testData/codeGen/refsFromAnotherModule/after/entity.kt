package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: ContentRootEntity?
}

@Parent val ContentRootEntity.ref: ReferredEntity
  by WorkspaceEntity.extension()