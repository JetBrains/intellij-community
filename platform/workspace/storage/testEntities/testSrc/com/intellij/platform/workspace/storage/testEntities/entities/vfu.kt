// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface VFUEntity : WorkspaceEntity {
  val data: String
  @IndexVfu
  val fileProperty: VirtualFileUrl
}

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
  val data: String
  @IndexVfu
  val fileProperty: VirtualFileUrl
  @IndexVfu
  val secondFileProperty: VirtualFileUrl
}

interface NullableVFUEntity : WorkspaceEntity {
  val data: String
  @IndexVfu
  val fileProperty: VirtualFileUrl?
}

interface ListVFUEntity : WorkspaceEntity {
  val data: String
  @IndexVfu
  val fileProperty: List<VirtualFileUrl>
}

interface SetVFUEntity : WorkspaceEntity {
  val data: String
  @IndexVfu
  val fileProperty: Set<VirtualFileUrl>
}

data class DataClassWithVfus(val vfu: VirtualFileUrl, val vfus: List<VirtualFileUrl>)

interface EntityWithDataClassWithVfu : WorkspaceEntity {
  @IndexVfu
  val singleDataClass: DataClassWithVfus
  @IndexVfu
  val listOfDataClass: List<DataClassWithVfus>
}

interface EntityWithUnindexedVfu : WorkspaceEntity {
  @IndexVfu
  val indexedVfu: VirtualFileUrl
  val unindexedVfu: VirtualFileUrl
  @IndexVfu
  val indexedDataClass: DataClassWithVfus
  val unindexedDataClass: DataClassWithVfus
}