// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface OneToOneRefEntity: WorkspaceEntity {
  val version: Int
  val text: String
  val anotherEntity: List<com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity> // Change is here, ONE_TO_ONE connection -> ONE_TO_MANY connection
}

interface AnotherOneToOneRefEntity: WorkspaceEntity {
  val someString: String
  val boolean: Boolean
  @Parent
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity
}
