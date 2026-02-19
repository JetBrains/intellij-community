package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
}