// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EnumPropsEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface EnumPropsEntityBuilder : WorkspaceEntityBuilder<EnumPropsEntity> {
  override var entitySource: EntitySource
  var someEnum: EnumPropsEnum
}

internal object EnumPropsEntityType : EntityType<EnumPropsEntity, EnumPropsEntityBuilder>() {
  override val entityClass: Class<EnumPropsEntity> get() = EnumPropsEntity::class.java
  operator fun invoke(
    someEnum: EnumPropsEnum,
    entitySource: EntitySource,
    init: (EnumPropsEntityBuilder.() -> Unit)? = null,
  ): EnumPropsEntityBuilder {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEnumPropsEntity(
  entity: EnumPropsEntity,
  modification: EnumPropsEntityBuilder.() -> Unit,
): EnumPropsEntity = modifyEntity(EnumPropsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEnumPropsEntity")
fun EnumPropsEntity(
  someEnum: EnumPropsEnum,
  entitySource: EntitySource,
  init: (EnumPropsEntityBuilder.() -> Unit)? = null,
): EnumPropsEntityBuilder = EnumPropsEntityType(someEnum, entitySource, init)
