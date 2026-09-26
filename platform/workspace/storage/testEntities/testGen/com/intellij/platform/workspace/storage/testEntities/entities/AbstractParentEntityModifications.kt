// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AbstractParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface AbstractParentEntityBuilder<T : AbstractParentEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var data: String
  var child: ChildWithExtensionParentBuilder?
}
