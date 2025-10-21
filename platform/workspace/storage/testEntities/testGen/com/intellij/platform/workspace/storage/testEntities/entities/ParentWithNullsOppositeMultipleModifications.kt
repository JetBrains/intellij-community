// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithNullsOppositeMultipleModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ParentWithNullsOppositeMultipleBuilder : WorkspaceEntityBuilder<ParentWithNullsOppositeMultiple> {
  override var entitySource: EntitySource
  var parentData: String
}

internal object ParentWithNullsOppositeMultipleType : EntityType<ParentWithNullsOppositeMultiple, ParentWithNullsOppositeMultipleBuilder>() {
  override val entityClass: Class<ParentWithNullsOppositeMultiple> get() = ParentWithNullsOppositeMultiple::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentWithNullsOppositeMultipleBuilder.() -> Unit)? = null,
  ): ParentWithNullsOppositeMultipleBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNullsOppositeMultiple(
  entity: ParentWithNullsOppositeMultiple,
  modification: ParentWithNullsOppositeMultipleBuilder.() -> Unit,
): ParentWithNullsOppositeMultiple = modifyEntity(ParentWithNullsOppositeMultipleBuilder::class.java, entity, modification)

var ParentWithNullsOppositeMultipleBuilder.children: List<ChildWithNullsOppositeMultipleBuilder>
  by WorkspaceEntity.extensionBuilder(ChildWithNullsOppositeMultiple::class.java)


@JvmOverloads
@JvmName("createParentWithNullsOppositeMultiple")
fun ParentWithNullsOppositeMultiple(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentWithNullsOppositeMultipleBuilder.() -> Unit)? = null,
): ParentWithNullsOppositeMultipleBuilder = ParentWithNullsOppositeMultipleType(parentData, entitySource, init)
