// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface MainEntityToParent : WorkspaceEntity {
  val x: String
  val child: AttachedEntityToParent?
  val childNullableParent: AttachedEntityToNullableParent?

}

interface AttachedEntityToParent : WorkspaceEntity {
  val data: String

}

@Parent
val AttachedEntityToParent.ref: MainEntityToParent
    by WorkspaceEntity.extension()


interface AttachedEntityToNullableParent: WorkspaceEntity {
  val data: String
}

@Parent
val AttachedEntityToNullableParent.nullableRef: MainEntityToParent?
  by WorkspaceEntity.extension()