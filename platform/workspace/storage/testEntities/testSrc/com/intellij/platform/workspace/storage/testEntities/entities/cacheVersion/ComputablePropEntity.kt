// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface ComputablePropEntity: WorkspaceEntity {
  val list: List<Map<List<Int?>, String>>
  val value: Int
  val computableText: String
    get() = "somePrefix${value}someSuffix"

}
