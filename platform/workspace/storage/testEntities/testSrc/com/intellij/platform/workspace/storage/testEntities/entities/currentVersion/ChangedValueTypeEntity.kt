// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface ChangedValueTypeEntity: WorkspaceEntity {
  val type: String
  val someKey: String //Change is here, id: Int --> id: String
  val text: List<String>
}
