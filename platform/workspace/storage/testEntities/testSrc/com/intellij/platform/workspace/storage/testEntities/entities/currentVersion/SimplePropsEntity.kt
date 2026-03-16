// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface SimplePropsEntity: WorkspaceEntity {
  val text: String
  val list: List<Int>
  val set: Set<List<String>>
  val map: Map<Set<String>, List<String>> // Change is here Set<Int> to Set<String>
  val bool: Boolean
}
