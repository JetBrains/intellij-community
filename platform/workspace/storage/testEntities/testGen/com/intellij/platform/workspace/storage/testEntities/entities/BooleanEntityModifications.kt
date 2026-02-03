// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BooleanEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface BooleanEntityBuilder : WorkspaceEntityBuilder<BooleanEntity> {
  override var entitySource: EntitySource
  var data: Boolean
}

internal object BooleanEntityType : EntityType<BooleanEntity, BooleanEntityBuilder>() {
  override val entityClass: Class<BooleanEntity> get() = BooleanEntity::class.java
  operator fun invoke(
    data: Boolean,
    entitySource: EntitySource,
    init: (BooleanEntityBuilder.() -> Unit)? = null,
  ): BooleanEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyBooleanEntity(
  entity: BooleanEntity,
  modification: BooleanEntityBuilder.() -> Unit,
): BooleanEntity = modifyEntity(BooleanEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createBooleanEntity")
fun BooleanEntity(
  data: Boolean,
  entitySource: EntitySource,
  init: (BooleanEntityBuilder.() -> Unit)? = null,
): BooleanEntityBuilder = BooleanEntityType(data, entitySource, init)
