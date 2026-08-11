package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EqualsBy
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface EntityWithKeyField : WorkspaceEntity {
  @EqualsBy
  val keyField: String
  val notKeyField: String
}