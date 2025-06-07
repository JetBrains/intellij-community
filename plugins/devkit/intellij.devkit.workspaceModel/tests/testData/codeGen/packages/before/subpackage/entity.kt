package com.intellij.workspaceModel.test.api.subpackage

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SubSimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
}