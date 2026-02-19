// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface OneToOneRefEntity: WorkspaceEntity {
  val version: Int
  val text: String
  val anotherEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity?
}

interface AnotherOneToOneRefEntity: WorkspaceEntity {
  val someString: String
  val boolean: Boolean
  @Parent
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity
}
