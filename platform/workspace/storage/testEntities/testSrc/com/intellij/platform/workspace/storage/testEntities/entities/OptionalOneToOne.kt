// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface OptionalOneToOneParentEntity : WorkspaceEntity {
  val child: OptionalOneToOneChildEntity?
}

interface OptionalOneToOneChildEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parent: OptionalOneToOneParentEntity?
}
