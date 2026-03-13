// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface OneToManyRefEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass
  val anotherEntity: List<com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity>
}

interface AnotherOneToManyRefEntity: WorkspaceEntity {
  @Parent
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity
  val version: Int
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass
}

data class OneToManyRefDataClass(val list: List<Set<String>>, val value: Int)