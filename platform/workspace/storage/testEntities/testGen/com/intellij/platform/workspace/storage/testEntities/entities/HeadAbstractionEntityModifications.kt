// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HeadAbstractionEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface HeadAbstractionEntityBuilder : WorkspaceEntityBuilder<HeadAbstractionEntity> {
  override var entitySource: EntitySource
  var data: String
  var child: CompositeBaseEntityBuilder<out CompositeBaseEntity>?
}

internal object HeadAbstractionEntityType : EntityType<HeadAbstractionEntity, HeadAbstractionEntityBuilder>() {
  override val entityClass: Class<HeadAbstractionEntity> get() = HeadAbstractionEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (HeadAbstractionEntityBuilder.() -> Unit)? = null,
  ): HeadAbstractionEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyHeadAbstractionEntity(
  entity: HeadAbstractionEntity,
  modification: HeadAbstractionEntityBuilder.() -> Unit,
): HeadAbstractionEntity = modifyEntity(HeadAbstractionEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createHeadAbstractionEntity")
fun HeadAbstractionEntity(
  data: String,
  entitySource: EntitySource,
  init: (HeadAbstractionEntityBuilder.() -> Unit)? = null,
): HeadAbstractionEntityBuilder = HeadAbstractionEntityType(data, entitySource, init)
