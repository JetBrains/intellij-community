package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SuperInterface {
  val url: VirtualFileUrl
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
