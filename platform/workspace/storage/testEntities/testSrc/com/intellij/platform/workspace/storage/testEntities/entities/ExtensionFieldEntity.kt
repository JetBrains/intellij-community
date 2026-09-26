// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface MainEntity : WorkspaceEntity {
  val x: String

}

interface AttachedEntity : WorkspaceEntity {
  @Parent
  val ref: MainEntity
  val data: String

}

val MainEntity.child: AttachedEntity?
    by WorkspaceEntity.extension()
