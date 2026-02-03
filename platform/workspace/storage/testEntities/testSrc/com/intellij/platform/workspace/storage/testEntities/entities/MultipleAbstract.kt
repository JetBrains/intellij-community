// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentAbEntity : WorkspaceEntity {
  val children: List<ChildAbstractBaseEntity>

}

@Abstract
interface ChildAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  @Parent
  val parentEntity: ParentAbEntity

}

interface ChildFirstEntity : ChildAbstractBaseEntity {
  val firstData: String

}

interface ChildSecondEntity : ChildAbstractBaseEntity {

  // TODO doesn't work at the moment
  //    override val commonData: String

  val secondData: String

}
