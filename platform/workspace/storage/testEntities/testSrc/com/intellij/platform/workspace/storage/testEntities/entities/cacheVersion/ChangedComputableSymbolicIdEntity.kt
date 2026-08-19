// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface ChangedComputableSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val text: String
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputableSymbolicId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputableSymbolicId(text)
}

data class ChangedComputableSymbolicId(val text: String) : SymbolicEntityId<ChangedComputableSymbolicIdEntity> {
  override val presentableName: String
    get() = text
}