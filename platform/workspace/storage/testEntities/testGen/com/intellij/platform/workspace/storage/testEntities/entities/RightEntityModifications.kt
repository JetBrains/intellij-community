// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RightEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.RightEntityImpl

@GeneratedCodeApiVersion(3)
interface RightEntityBuilder : WorkspaceEntityBuilder<RightEntity>, CompositeBaseEntityBuilder<RightEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
  override var children: List<BaseEntityBuilder<out BaseEntity>>
  override var parent: HeadAbstractionEntityBuilder?
}

internal object RightEntityType : EntityType<RightEntity, RightEntityBuilder>() {
  override val entityClass: Class<RightEntity> get() = RightEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = RightEntityImpl.Builder::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (RightEntityBuilder.() -> Unit)? = null,
  ): RightEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyRightEntity(
  entity: RightEntity,
  modification: RightEntityBuilder.() -> Unit,
): RightEntity = modifyEntity(RightEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createRightEntity")
fun RightEntity(
  entitySource: EntitySource,
  init: (RightEntityBuilder.() -> Unit)? = null,
): RightEntityBuilder = RightEntityType(entitySource, init)
