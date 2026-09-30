package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SuperInterface {
  val something: String
  val hasSuper: Boolean
}

interface SuperSuperInterface : SuperInterface {
  val hasSuperSuper: Boolean
}

interface EmptyCustomEntity : WorkspaceEntity, SuperInterface

interface CustomEntity : WorkspaceEntity, SuperSuperInterface {
  val name: String
  override val hasSuper: Boolean
}
