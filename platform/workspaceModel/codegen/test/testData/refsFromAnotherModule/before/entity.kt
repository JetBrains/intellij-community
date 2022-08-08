package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: ContentRootEntity?
}

val ContentRootEntity.ref: @Child ReferredEntity
  by WorkspaceEntity.extension()