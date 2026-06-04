// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompositeAbstractEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface CompositeAbstractEntityBuilder<T : CompositeAbstractEntity> : WorkspaceEntityBuilder<T>, SimpleAbstractEntityBuilder<T> {
  override var entitySource: EntitySource
  override var parentInList: CompositeAbstractEntityBuilder<out CompositeAbstractEntity>?
  var children: List<SimpleAbstractEntityBuilder<out SimpleAbstractEntity>>
  var parentEntity: ParentChainEntityBuilder?
}
