// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentMultipleEntity : WorkspaceEntity {
  val parentData: String
  val children: List<ChildMultipleEntity>

}

interface ChildMultipleEntity : WorkspaceEntity {
  val childData: String

  @Parent
  val parentEntity: ParentMultipleEntity

}
