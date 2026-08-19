// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

/**
 * In this test we can deserialize cache
 * 
 * currentVersion:
 * - computableInt, someKey, symbolicId, names, value, computableString
 * 
 * cacheVersion:
 * - someKey, computableString, names, symbolicId, value, computableInt
 */
interface ChangedComputablePropsOrderEntity: WorkspaceEntityWithSymbolicId {
  val someKey: Int
  val computableString: String
    get() = "id = $someKey"
  val names: List<String>
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId(names)
  val value: Int
  val computableInt: Int
    get() = someKey + value
}

data class ChangedComputablePropsOrderEntityId(val names: List<String>): SymbolicEntityId<ChangedComputablePropsOrderEntity> {
  override val presentableName: String
    get() = names.toString()
}