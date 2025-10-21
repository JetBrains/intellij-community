// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IntEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface IntEntityBuilder : WorkspaceEntityBuilder<IntEntity> {
  override var entitySource: EntitySource
  var data: Int
}

internal object IntEntityType : EntityType<IntEntity, IntEntityBuilder>() {
  override val entityClass: Class<IntEntity> get() = IntEntity::class.java
  operator fun invoke(
    data: Int,
    entitySource: EntitySource,
    init: (IntEntityBuilder.() -> Unit)? = null,
  ): IntEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyIntEntity(
  entity: IntEntity,
  modification: IntEntityBuilder.() -> Unit,
): IntEntity = modifyEntity(IntEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createIntEntity")
fun IntEntity(
  data: Int,
  entitySource: EntitySource,
  init: (IntEntityBuilder.() -> Unit)? = null,
): IntEntityBuilder = IntEntityType(data, entitySource, init)
