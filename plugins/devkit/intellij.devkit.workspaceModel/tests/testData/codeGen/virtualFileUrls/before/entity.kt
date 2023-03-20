package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

interface EntityWithUrls : WorkspaceEntity {
  val simpleUrl: VirtualFileUrl
  val nullableUrl: VirtualFileUrl?
  val listOfUrls: List<VirtualFileUrl>
  val dataClassWithUrl: DataClassWithUrl
}

data class DataClassWithUrl(val url: VirtualFileUrl)