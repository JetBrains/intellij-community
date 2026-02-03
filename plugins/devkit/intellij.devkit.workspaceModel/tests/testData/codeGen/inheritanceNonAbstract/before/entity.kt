package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface LegalEntity : WorkspaceEntity {
  val property: String
}

interface IllegalEntity : LegalEntity {
  val anotherProperty: String
}
