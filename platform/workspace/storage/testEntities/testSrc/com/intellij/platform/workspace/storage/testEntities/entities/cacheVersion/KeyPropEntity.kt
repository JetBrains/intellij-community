// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

// In this test we can deserialize cache
interface KeyPropEntity: WorkspaceEntity {
  val someInt: Int
  val text: String
  @EqualsBy
  val url: VirtualFileUrl
}
