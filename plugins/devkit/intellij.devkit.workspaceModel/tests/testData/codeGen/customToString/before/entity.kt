package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.ToString

interface WithCustomToStringEntity : WorkspaceEntity {
  val myName: String
  val version: Int

  @ToString
  val stringRepresentation: String
    get() = "Custom entity toString: $myName & $version"
}