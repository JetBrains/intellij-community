package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

data class DataClassWithVfu(
  val version: Int,
  val virtualFileUrl: VirtualFileUrl
)

data class DataClassWithDataClassWithVfu(
  val version: Int,
  val virtualFileUrl: VirtualFileUrl,
  val dataclassWithVfu: DataClassWithVfu
)

data class DeepVfuList(val deepVfuList: List<VirtualFileUrl>)

data class DataClassWithVfuCollections(
  val version: Int,
  val vfuCollection: List<VirtualFileUrl>,
  val dataClassCollection: List<DataClassWithDataClassWithVfu>,
  val deepVfuListCollection: List<DeepVfuList>
)

interface EntityWithVariousVirtualFileUrls : WorkspaceEntity {
  val name: String
  val version: Int
  // indexed vfus
  @IndexVfu
  val someUrl: VirtualFileUrl
  @IndexVfu
  val nullableUrl: VirtualFileUrl?
  @IndexVfu
  val dataClassWithVfu: DataClassWithVfu
  @IndexVfu
  val dataClassWithDataClassWithVfu: DataClassWithDataClassWithVfu
  @IndexVfu
  val dataClassWithVfuCollection: DataClassWithVfuCollections
  @IndexVfu
  val collectionOfVfuCollections: List<DataClassWithVfuCollections>
  // unindexed vfus
  val unindexedVfu: VirtualFileUrl
  val unindexedVfuCollection: List<VirtualFileUrl>
  val unindexedDataClassWithVfu: DataClassWithVfu
}