// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface OneToManyRefEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass
  val anotherEntity: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity? //Change is here, ONE_TO_MANY connection -> ONE_TO_ONE connection
}

interface AnotherOneToManyRefEntity: WorkspaceEntity {
  @Parent
  val parentEntity: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity
  val version: Int
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass
}

data class OneToManyRefDataClass(val list: List<Set<String>>, val value: Int)