// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildWithNullsOppositeMultipleModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildWithNullsOppositeMultipleBuilder : WorkspaceEntityBuilder<ChildWithNullsOppositeMultiple> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ParentWithNullsOppositeMultipleBuilder?
}

internal object ChildWithNullsOppositeMultipleType : EntityType<ChildWithNullsOppositeMultiple, ChildWithNullsOppositeMultipleBuilder>() {
  override val entityClass: Class<ChildWithNullsOppositeMultiple> get() = ChildWithNullsOppositeMultiple::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ChildWithNullsOppositeMultipleBuilder.() -> Unit)? = null,
  ): ChildWithNullsOppositeMultipleBuilder {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithNullsOppositeMultiple(
  entity: ChildWithNullsOppositeMultiple,
  modification: ChildWithNullsOppositeMultipleBuilder.() -> Unit,
): ChildWithNullsOppositeMultiple = modifyEntity(ChildWithNullsOppositeMultipleBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildWithNullsOppositeMultiple")
fun ChildWithNullsOppositeMultiple(
  childData: String,
  entitySource: EntitySource,
  init: (ChildWithNullsOppositeMultipleBuilder.() -> Unit)? = null,
): ChildWithNullsOppositeMultipleBuilder = ChildWithNullsOppositeMultipleType(childData, entitySource, init)
