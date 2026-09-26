package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface MainEntity : WorkspaceEntity {
  val property: String
  <error descr="Unsupported 'var' field in an entity">var isValid: Boolean</error>
}