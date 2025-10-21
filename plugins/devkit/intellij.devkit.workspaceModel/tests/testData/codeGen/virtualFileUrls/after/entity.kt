package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface EntityWithUrls : WorkspaceEntity {
  val simpleUrl: VirtualFileUrl
  val nullableUrl: VirtualFileUrl?
  val listOfUrls: List<VirtualFileUrl>
  val dataClassWithUrl: DataClassWithUrl
}

data class DataClassWithUrl(val url: VirtualFileUrl)