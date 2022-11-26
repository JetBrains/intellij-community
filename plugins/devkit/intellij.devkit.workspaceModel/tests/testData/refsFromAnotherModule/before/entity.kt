package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import org.jetbrains.deft.annotations.Child

interface ReferredEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val contentRoot: @Child ContentRootEntity?
}

val ContentRootEntity.ref: ReferredEntity
  by WorkspaceEntity.extension()