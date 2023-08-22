package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface DefaultFieldEntity : WorkspaceEntity {
  val version: Int
  val data: TestData
  val anotherVersion: Int
    @Default get() = 0
  val description: String
    @Default get() = "Default description"
}

data class TestData(val name: String, val description: String)