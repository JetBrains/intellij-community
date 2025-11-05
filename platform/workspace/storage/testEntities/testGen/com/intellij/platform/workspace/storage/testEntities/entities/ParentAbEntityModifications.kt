// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentAbEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentAbEntityBuilder : WorkspaceEntityBuilder<ParentAbEntity> {
  override var entitySource: EntitySource
  var children: List<ChildAbstractBaseEntityBuilder<out ChildAbstractBaseEntity>>
}

internal object ParentAbEntityType : EntityType<ParentAbEntity, ParentAbEntityBuilder>() {
  override val entityClass: Class<ParentAbEntity> get() = ParentAbEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ParentAbEntityBuilder.() -> Unit)? = null,
  ): ParentAbEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentAbEntity(
  entity: ParentAbEntity,
  modification: ParentAbEntityBuilder.() -> Unit,
): ParentAbEntity = modifyEntity(ParentAbEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentAbEntity")
fun ParentAbEntity(
  entitySource: EntitySource,
  init: (ParentAbEntityBuilder.() -> Unit)? = null,
): ParentAbEntityBuilder = ParentAbEntityType(entitySource, init)
