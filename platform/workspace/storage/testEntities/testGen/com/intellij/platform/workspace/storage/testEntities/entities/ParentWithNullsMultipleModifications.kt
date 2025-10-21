// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithNullsMultipleModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentWithNullsMultipleBuilder : WorkspaceEntityBuilder<ParentWithNullsMultiple> {
  override var entitySource: EntitySource
  var parentData: String
  var children: List<ChildWithNullsMultipleBuilder>
}

internal object ParentWithNullsMultipleType : EntityType<ParentWithNullsMultiple, ParentWithNullsMultipleBuilder>() {
  override val entityClass: Class<ParentWithNullsMultiple> get() = ParentWithNullsMultiple::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentWithNullsMultipleBuilder.() -> Unit)? = null,
  ): ParentWithNullsMultipleBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNullsMultiple(
  entity: ParentWithNullsMultiple,
  modification: ParentWithNullsMultipleBuilder.() -> Unit,
): ParentWithNullsMultiple = modifyEntity(ParentWithNullsMultipleBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithNullsMultiple")
fun ParentWithNullsMultiple(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentWithNullsMultipleBuilder.() -> Unit)? = null,
): ParentWithNullsMultipleBuilder = ParentWithNullsMultipleType(parentData, entitySource, init)
