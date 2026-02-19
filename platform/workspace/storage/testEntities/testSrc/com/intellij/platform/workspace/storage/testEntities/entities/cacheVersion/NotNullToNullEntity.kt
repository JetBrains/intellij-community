// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

// In this test we can deserialize cache
interface NotNullToNullEntity: WorkspaceEntity {
  val nullInt: Int?
  val notNullString: String
  val notNullList: List<Int>
}
