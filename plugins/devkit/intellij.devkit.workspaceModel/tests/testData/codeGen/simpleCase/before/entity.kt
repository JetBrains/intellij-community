package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  val char: Char
  val long: Long
  val float: Float
  val double: Double
  val short: Short
  val byte: Byte
}