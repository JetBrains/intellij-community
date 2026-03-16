// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default


interface DefaultValueEntity: WorkspaceEntity {
  val name: String
  val isGenerated: Boolean
    @Default get() = true
  val anotherName: String
    @Default get() = "Another Text"

}
