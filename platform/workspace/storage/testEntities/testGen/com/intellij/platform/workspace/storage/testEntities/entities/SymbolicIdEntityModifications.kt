// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SymbolicIdEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SymbolicIdEntityBuilder : WorkspaceEntityBuilder<SymbolicIdEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object SymbolicIdEntityType : EntityType<SymbolicIdEntity, SymbolicIdEntityBuilder>() {
  override val entityClass: Class<SymbolicIdEntity> get() = SymbolicIdEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SymbolicIdEntityBuilder.() -> Unit)? = null,
  ): SymbolicIdEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySymbolicIdEntity(
  entity: SymbolicIdEntity,
  modification: SymbolicIdEntityBuilder.() -> Unit,
): SymbolicIdEntity = modifyEntity(SymbolicIdEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSymbolicIdEntity")
fun SymbolicIdEntity(
  data: String,
  entitySource: EntitySource,
  init: (SymbolicIdEntityBuilder.() -> Unit)? = null,
): SymbolicIdEntityBuilder = SymbolicIdEntityType(data, entitySource, init)
