package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
}

val SimpleEntity.nonWsmExtension1: Int?
  get() = name.length

val SimpleEntity.nonWsmExtension2: List<Boolean>
  get() = listOf(true, false)
