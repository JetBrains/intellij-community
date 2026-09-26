package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

internal interface InternalEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
}

private interface PrivateEntity : WorkspaceEntity {
  val name: String
}