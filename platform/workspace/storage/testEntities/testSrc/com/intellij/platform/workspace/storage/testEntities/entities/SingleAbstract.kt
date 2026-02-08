// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentSingleAbEntity : WorkspaceEntity {
  val child: ChildSingleAbstractBaseEntity?

}

@Abstract
interface ChildSingleAbstractBaseEntity : WorkspaceEntity {
  val commonData: String

  @Parent
  val parentEntity: ParentSingleAbEntity

}

interface ChildSingleFirstEntity : ChildSingleAbstractBaseEntity {
  val firstData: String

}

interface ChildSingleSecondEntity : ChildSingleAbstractBaseEntity {
  val secondData: String

}
