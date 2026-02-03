// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface ChangedComputablePropEntity: WorkspaceEntityWithSymbolicId {
  val text: String
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId(text)
}

data class ChangedComputablePropEntityId(val text: String): SymbolicEntityId<ChangedComputablePropEntity> {
  override val presentableName: String
    get() = text
}