// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NullableVFUEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface NullableVFUEntityBuilder : WorkspaceEntityBuilder<NullableVFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: VirtualFileUrl?
}

internal object NullableVFUEntityType : EntityType<NullableVFUEntity, NullableVFUEntityBuilder>() {
  override val entityClass: Class<NullableVFUEntity> get() = NullableVFUEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (NullableVFUEntityBuilder.() -> Unit)? = null,
  ): NullableVFUEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNullableVFUEntity(
  entity: NullableVFUEntity,
  modification: NullableVFUEntityBuilder.() -> Unit,
): NullableVFUEntity = modifyEntity(NullableVFUEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNullableVFUEntity")
fun NullableVFUEntity(
  data: String,
  entitySource: EntitySource,
  init: (NullableVFUEntityBuilder.() -> Unit)? = null,
): NullableVFUEntityBuilder = NullableVFUEntityType(data, entitySource, init)
