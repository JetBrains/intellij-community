package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface MainEntity : WorkspaceEntity {
  val property: String
  var isValid: Boolean
}