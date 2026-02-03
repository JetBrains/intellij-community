// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimplePropsEntity: WorkspaceEntity {
  val text: String
  val list: List<Int>
  val set: Set<List<String>>
  val map: Map<Set<Int>, List<String>>
  val bool: Boolean
}
