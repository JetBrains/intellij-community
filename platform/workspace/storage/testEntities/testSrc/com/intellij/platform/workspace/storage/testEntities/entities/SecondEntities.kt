// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?
}

interface VFUEntity2 : WorkspaceEntity {
  val data: String
  @IndexVfu
  val filePath: VirtualFileUrl?
  @IndexVfu
  val directoryPath: VirtualFileUrl
  @IndexVfu
  val notNullRoots: List<VirtualFileUrl>
}
