// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UnknownFieldEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import java.util.Date

@GeneratedCodeApiVersion(3)
interface UnknownFieldEntityBuilder : WorkspaceEntityBuilder<UnknownFieldEntity> {
  override var entitySource: EntitySource
  var data: Date
}

internal object UnknownFieldEntityType : EntityType<UnknownFieldEntity, UnknownFieldEntityBuilder>() {
  override val entityClass: Class<UnknownFieldEntity> get() = UnknownFieldEntity::class.java
  operator fun invoke(
    data: Date,
    entitySource: EntitySource,
    init: (UnknownFieldEntityBuilder.() -> Unit)? = null,
  ): UnknownFieldEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyUnknownFieldEntity(
  entity: UnknownFieldEntity,
  modification: UnknownFieldEntityBuilder.() -> Unit,
): UnknownFieldEntity = modifyEntity(UnknownFieldEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createUnknownFieldEntity")
fun UnknownFieldEntity(
  data: Date,
  entitySource: EntitySource,
  init: (UnknownFieldEntityBuilder.() -> Unit)? = null,
): UnknownFieldEntityBuilder = UnknownFieldEntityType(data, entitySource, init)
