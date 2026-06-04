// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AbstractChildWithLinkToParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface AbstractChildWithLinkToParentEntityBuilder<T : AbstractChildWithLinkToParentEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var data: String
}

@Parent
var AbstractChildWithLinkToParentEntityBuilder<out AbstractChildWithLinkToParentEntity>.parent: ParentWithLinkToAbstractChildBuilder?
  by WorkspaceEntity.extensionBuilder(ParentWithLinkToAbstractChild::class.java)

