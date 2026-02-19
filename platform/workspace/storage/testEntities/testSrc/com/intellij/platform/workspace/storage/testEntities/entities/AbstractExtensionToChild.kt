// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

interface ParentWithExtensionEntity : WorkspaceEntity {
  val data: String
}

@Abstract
interface AbstractChildEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parent: ParentWithExtensionEntity
}

interface SpecificChildEntity : AbstractChildEntity {
}

val ParentWithExtensionEntity.child: AbstractChildEntity? by WorkspaceEntity.extension()