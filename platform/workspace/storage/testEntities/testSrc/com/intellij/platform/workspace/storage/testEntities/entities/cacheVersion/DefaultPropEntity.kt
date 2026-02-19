// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default

// In this test we can deserialize cache
interface DefaultPropEntity: WorkspaceEntity {
  val someString: String
  val someList: List<Int>
  val constInt: Int
    @Default get() = 9
}
