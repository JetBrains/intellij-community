package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface IllegalEntity : WorkspaceEntity, EntitySource {
  val property: String
}
