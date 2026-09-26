// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface ChangedComputableSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val text: String
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputableSymbolicId(listOf(text,
                                                                                                                            "more text",
                                                                                                                            text))
}

data class ChangedComputableSymbolicId(val texts: List<String>) : SymbolicEntityId<ChangedComputableSymbolicIdEntity> {
  override val presentableName: String
    get() = texts.joinToString(", ")
}