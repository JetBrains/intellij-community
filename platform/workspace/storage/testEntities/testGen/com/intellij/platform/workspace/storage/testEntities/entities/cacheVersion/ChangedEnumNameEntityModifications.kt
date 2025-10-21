// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedEnumNameEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChangedEnumNameEntityBuilder : WorkspaceEntityBuilder<ChangedEnumNameEntity> {
  override var entitySource: EntitySource
  var someEnum: ChangedEnumNameEnum
}

internal object ChangedEnumNameEntityType : EntityType<ChangedEnumNameEntity, ChangedEnumNameEntityBuilder>() {
  override val entityClass: Class<ChangedEnumNameEntity> get() = ChangedEnumNameEntity::class.java
  operator fun invoke(
    someEnum: ChangedEnumNameEnum,
    entitySource: EntitySource,
    init: (ChangedEnumNameEntityBuilder.() -> Unit)? = null,
  ): ChangedEnumNameEntityBuilder {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedEnumNameEntity(
  entity: ChangedEnumNameEntity,
  modification: ChangedEnumNameEntityBuilder.() -> Unit,
): ChangedEnumNameEntity = modifyEntity(ChangedEnumNameEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedEnumNameEntity")
fun ChangedEnumNameEntity(
  someEnum: ChangedEnumNameEnum,
  entitySource: EntitySource,
  init: (ChangedEnumNameEntityBuilder.() -> Unit)? = null,
): ChangedEnumNameEntityBuilder = ChangedEnumNameEntityType(someEnum, entitySource, init)
