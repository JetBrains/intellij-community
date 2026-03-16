// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

// In this test we can deserialize cache
interface ChangedComputablePropsOrderEntity: WorkspaceEntityWithSymbolicId {
  val computableInt: Int
    get() = someKey + value
  val someKey: Int
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropsOrderEntityId(names)
  val names: List<String>
  val value: Int
  val computableString: String
    get() = "id = $someKey"
}

data class ChangedComputablePropsOrderEntityId(val names: List<String>): SymbolicEntityId<ChangedComputablePropsOrderEntity> {
  override val presentableName: String
    get() = names.toString()
}