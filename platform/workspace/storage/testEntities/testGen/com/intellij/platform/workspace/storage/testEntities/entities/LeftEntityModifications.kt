// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LeftEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface LeftEntityBuilder : WorkspaceEntityBuilder<LeftEntity>, CompositeBaseEntityBuilder<LeftEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
  override var children: List<BaseEntityBuilder<out BaseEntity>>
  override var parent: HeadAbstractionEntityBuilder?
}

internal object LeftEntityType : EntityType<LeftEntity, LeftEntityBuilder>() {
  override val entityClass: Class<LeftEntity> get() = LeftEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (LeftEntityBuilder.() -> Unit)? = null,
  ): LeftEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyLeftEntity(
  entity: LeftEntity,
  modification: LeftEntityBuilder.() -> Unit,
): LeftEntity = modifyEntity(LeftEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createLeftEntity")
fun LeftEntity(
  entitySource: EntitySource,
  init: (LeftEntityBuilder.() -> Unit)? = null,
): LeftEntityBuilder = LeftEntityType(entitySource, init)
