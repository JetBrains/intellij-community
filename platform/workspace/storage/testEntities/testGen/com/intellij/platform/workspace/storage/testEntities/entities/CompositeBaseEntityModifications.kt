// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompositeBaseEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface CompositeBaseEntityBuilder<T : CompositeBaseEntity> : WorkspaceEntityBuilder<T>, BaseEntityBuilder<T> {
  override var entitySource: EntitySource
  override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
  var children: List<BaseEntityBuilder<out BaseEntity>>
  var parent: HeadAbstractionEntityBuilder?
}
