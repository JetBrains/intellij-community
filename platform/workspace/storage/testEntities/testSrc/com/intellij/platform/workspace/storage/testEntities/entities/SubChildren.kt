// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentSubEntity : WorkspaceEntity {
  val parentData: String

  val child: ChildSubEntity?

}

interface ChildSubEntity : WorkspaceEntity {
  @Parent
  val parentEntity: ParentSubEntity

  val child: ChildSubSubEntity?

}

interface ChildSubSubEntity : WorkspaceEntity {
  @Parent
  val parentEntity: ChildSubEntity

  val childData: String

}
