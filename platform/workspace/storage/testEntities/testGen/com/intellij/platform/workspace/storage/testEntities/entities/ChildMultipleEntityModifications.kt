// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildMultipleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildMultipleEntityBuilder : WorkspaceEntityBuilder<ChildMultipleEntity> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ParentMultipleEntityBuilder
}

internal object ChildMultipleEntityType : EntityType<ChildMultipleEntity, ChildMultipleEntityBuilder>() {
  override val entityClass: Class<ChildMultipleEntity> get() = ChildMultipleEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ChildMultipleEntityBuilder.() -> Unit)? = null,
  ): ChildMultipleEntityBuilder {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildMultipleEntity(
  entity: ChildMultipleEntity,
  modification: ChildMultipleEntityBuilder.() -> Unit,
): ChildMultipleEntity = modifyEntity(ChildMultipleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildMultipleEntity")
fun ChildMultipleEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ChildMultipleEntityBuilder.() -> Unit)? = null,
): ChildMultipleEntityBuilder = ChildMultipleEntityType(childData, entitySource, init)
