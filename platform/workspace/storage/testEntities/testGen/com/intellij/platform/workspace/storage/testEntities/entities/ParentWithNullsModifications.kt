// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithNullsModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentWithNullsBuilder : WorkspaceEntityBuilder<ParentWithNulls> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ChildWithNullsBuilder?
}

internal object ParentWithNullsType : EntityType<ParentWithNulls, ParentWithNullsBuilder>() {
  override val entityClass: Class<ParentWithNulls> get() = ParentWithNulls::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ParentWithNullsBuilder.() -> Unit)? = null,
  ): ParentWithNullsBuilder {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNulls(
  entity: ParentWithNulls,
  modification: ParentWithNullsBuilder.() -> Unit,
): ParentWithNulls = modifyEntity(ParentWithNullsBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithNulls")
fun ParentWithNulls(
  parentData: String,
  entitySource: EntitySource,
  init: (ParentWithNullsBuilder.() -> Unit)? = null,
): ParentWithNullsBuilder = ParentWithNullsType(parentData, entitySource, init)
