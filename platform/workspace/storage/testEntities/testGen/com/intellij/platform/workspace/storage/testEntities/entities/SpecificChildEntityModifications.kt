// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SpecificChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SpecificChildEntityBuilder : WorkspaceEntityBuilder<SpecificChildEntity>, AbstractChildEntityBuilder<SpecificChildEntity> {
  override var entitySource: EntitySource
  override var data: String
  override var parent: ParentWithExtensionEntityBuilder
}

internal object SpecificChildEntityType : EntityType<SpecificChildEntity, SpecificChildEntityBuilder>() {
  override val entityClass: Class<SpecificChildEntity> get() = SpecificChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SpecificChildEntityBuilder.() -> Unit)? = null,
  ): SpecificChildEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificChildEntity(
  entity: SpecificChildEntity,
  modification: SpecificChildEntityBuilder.() -> Unit,
): SpecificChildEntity = modifyEntity(SpecificChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificChildEntity")
fun SpecificChildEntity(
  data: String,
  entitySource: EntitySource,
  init: (SpecificChildEntityBuilder.() -> Unit)? = null,
): SpecificChildEntityBuilder = SpecificChildEntityType(data, entitySource, init)
