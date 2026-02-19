// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent


// THESE ENTITIES ARE INCORRECTLY GENERATED. SEE IDEA-327859

interface ChildWithExtensionParent : WorkspaceEntity {
  val data: String
}

@Abstract
interface AbstractParentEntity : WorkspaceEntity {
  val data: String
  val child: ChildWithExtensionParent?
}

interface SpecificParent : AbstractParentEntity {
}

@Parent
val ChildWithExtensionParent.parent: AbstractParentEntity?
  by WorkspaceEntity.extension()