// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NullToNotNullEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface NullToNotNullEntityBuilder : WorkspaceEntityBuilder<NullToNotNullEntity> {
  override var entitySource: EntitySource
  var nullString: String?
  var notNullBoolean: Boolean
  var notNullInt: Int
}

internal object NullToNotNullEntityType : EntityType<NullToNotNullEntity, NullToNotNullEntityBuilder>() {
  override val entityClass: Class<NullToNotNullEntity> get() = NullToNotNullEntity::class.java
  operator fun invoke(
    notNullBoolean: Boolean,
    notNullInt: Int,
    entitySource: EntitySource,
    init: (NullToNotNullEntityBuilder.() -> Unit)? = null,
  ): NullToNotNullEntityBuilder {
    val builder = builder()
    builder.notNullBoolean = notNullBoolean
    builder.notNullInt = notNullInt
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNullToNotNullEntity(
  entity: NullToNotNullEntity,
  modification: NullToNotNullEntityBuilder.() -> Unit,
): NullToNotNullEntity = modifyEntity(NullToNotNullEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNullToNotNullEntity")
fun NullToNotNullEntity(
  notNullBoolean: Boolean,
  notNullInt: Int,
  entitySource: EntitySource,
  init: (NullToNotNullEntityBuilder.() -> Unit)? = null,
): NullToNotNullEntityBuilder = NullToNotNullEntityType(notNullBoolean, notNullInt, entitySource, init)
