// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SpecificChildWithLinkToParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SpecificChildWithLinkToParentEntityBuilder : WorkspaceEntityBuilder<SpecificChildWithLinkToParentEntity>, AbstractChildWithLinkToParentEntityBuilder<SpecificChildWithLinkToParentEntity> {
  override var entitySource: EntitySource
  override var data: String
}

internal object SpecificChildWithLinkToParentEntityType : EntityType<SpecificChildWithLinkToParentEntity, SpecificChildWithLinkToParentEntityBuilder>() {
  override val entityClass: Class<SpecificChildWithLinkToParentEntity> get() = SpecificChildWithLinkToParentEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SpecificChildWithLinkToParentEntityBuilder.() -> Unit)? = null,
  ): SpecificChildWithLinkToParentEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificChildWithLinkToParentEntity(
  entity: SpecificChildWithLinkToParentEntity,
  modification: SpecificChildWithLinkToParentEntityBuilder.() -> Unit,
): SpecificChildWithLinkToParentEntity = modifyEntity(SpecificChildWithLinkToParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificChildWithLinkToParentEntity")
fun SpecificChildWithLinkToParentEntity(
  data: String,
  entitySource: EntitySource,
  init: (SpecificChildWithLinkToParentEntityBuilder.() -> Unit)? = null,
): SpecificChildWithLinkToParentEntityBuilder = SpecificChildWithLinkToParentEntityType(data, entitySource, init)
