// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSubEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildSubEntityBuilder : WorkspaceEntityBuilder<ChildSubEntity> {
  override var entitySource: EntitySource
  var parentEntity: ParentSubEntityBuilder
  var child: ChildSubSubEntityBuilder?
}

internal object ChildSubEntityType : EntityType<ChildSubEntity, ChildSubEntityBuilder>() {
  override val entityClass: Class<ChildSubEntity> get() = ChildSubEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ChildSubEntityBuilder.() -> Unit)? = null,
  ): ChildSubEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSubEntity(
  entity: ChildSubEntity,
  modification: ChildSubEntityBuilder.() -> Unit,
): ChildSubEntity = modifyEntity(ChildSubEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSubEntity")
fun ChildSubEntity(
  entitySource: EntitySource,
  init: (ChildSubEntityBuilder.() -> Unit)? = null,
): ChildSubEntityBuilder = ChildSubEntityType(entitySource, init)
