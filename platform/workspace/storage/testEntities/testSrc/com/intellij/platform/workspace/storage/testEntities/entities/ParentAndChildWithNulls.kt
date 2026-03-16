// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentWithNulls : WorkspaceEntity {
  val parentData: String

  val child: ChildWithNulls?

}

interface ChildWithNulls : WorkspaceEntity {
  val childData: String

}

@Parent
val ChildWithNulls.parentEntity: ParentWithNulls?
    by WorkspaceEntity.extension()
