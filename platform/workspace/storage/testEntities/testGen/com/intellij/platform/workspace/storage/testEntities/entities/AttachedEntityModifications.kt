// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AttachedEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface AttachedEntityBuilder : WorkspaceEntityBuilder<AttachedEntity> {
  override var entitySource: EntitySource
  var ref: MainEntityBuilder
  var data: String
}

internal object AttachedEntityType : EntityType<AttachedEntity, AttachedEntityBuilder>() {
  override val entityClass: Class<AttachedEntity> get() = AttachedEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AttachedEntityBuilder.() -> Unit)? = null,
  ): AttachedEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntity(
  entity: AttachedEntity,
  modification: AttachedEntityBuilder.() -> Unit,
): AttachedEntity = modifyEntity(AttachedEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createAttachedEntity")
fun AttachedEntity(
  data: String,
  entitySource: EntitySource,
  init: (AttachedEntityBuilder.() -> Unit)? = null,
): AttachedEntityBuilder = AttachedEntityType(data, entitySource, init)
