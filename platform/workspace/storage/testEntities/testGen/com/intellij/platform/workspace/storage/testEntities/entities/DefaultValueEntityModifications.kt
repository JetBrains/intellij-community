// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DefaultValueEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface DefaultValueEntityBuilder : WorkspaceEntityBuilder<DefaultValueEntity> {
  override var entitySource: EntitySource
  var name: String
  var isGenerated: Boolean
  var anotherName: String
}

internal object DefaultValueEntityType : EntityType<DefaultValueEntity, DefaultValueEntityBuilder>() {
  override val entityClass: Class<DefaultValueEntity> get() = DefaultValueEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (DefaultValueEntityBuilder.() -> Unit)? = null,
  ): DefaultValueEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyDefaultValueEntity(
  entity: DefaultValueEntity,
  modification: DefaultValueEntityBuilder.() -> Unit,
): DefaultValueEntity = modifyEntity(DefaultValueEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createDefaultValueEntity")
fun DefaultValueEntity(
  name: String,
  entitySource: EntitySource,
  init: (DefaultValueEntityBuilder.() -> Unit)? = null,
): DefaultValueEntityBuilder = DefaultValueEntityType(name, entitySource, init)
