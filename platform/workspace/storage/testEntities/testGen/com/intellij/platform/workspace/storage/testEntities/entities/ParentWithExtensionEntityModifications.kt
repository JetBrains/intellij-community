// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithExtensionEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ParentWithExtensionEntityBuilder : WorkspaceEntityBuilder<ParentWithExtensionEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object ParentWithExtensionEntityType : EntityType<ParentWithExtensionEntity, ParentWithExtensionEntityBuilder>() {
  override val entityClass: Class<ParentWithExtensionEntity> get() = ParentWithExtensionEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ParentWithExtensionEntityBuilder.() -> Unit)? = null,
  ): ParentWithExtensionEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithExtensionEntity(
  entity: ParentWithExtensionEntity,
  modification: ParentWithExtensionEntityBuilder.() -> Unit,
): ParentWithExtensionEntity = modifyEntity(ParentWithExtensionEntityBuilder::class.java, entity, modification)

var ParentWithExtensionEntityBuilder.child: AbstractChildEntityBuilder<out AbstractChildEntity>?
  by WorkspaceEntity.extensionBuilder(AbstractChildEntity::class.java)


@JvmOverloads
@JvmName("createParentWithExtensionEntity")
fun ParentWithExtensionEntity(
  data: String,
  entitySource: EntitySource,
  init: (ParentWithExtensionEntityBuilder.() -> Unit)? = null,
): ParentWithExtensionEntityBuilder = ParentWithExtensionEntityType(data, entitySource, init)
