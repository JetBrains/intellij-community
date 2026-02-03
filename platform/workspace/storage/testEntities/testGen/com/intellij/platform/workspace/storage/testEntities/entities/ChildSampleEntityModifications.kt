// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSampleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ChildSampleEntityBuilder : WorkspaceEntityBuilder<ChildSampleEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: SampleEntityBuilder?
}

internal object ChildSampleEntityType : EntityType<ChildSampleEntity, ChildSampleEntityBuilder>() {
  override val entityClass: Class<ChildSampleEntity> get() = ChildSampleEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ChildSampleEntityBuilder.() -> Unit)? = null,
  ): ChildSampleEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSampleEntity(
  entity: ChildSampleEntity,
  modification: ChildSampleEntityBuilder.() -> Unit,
): ChildSampleEntity = modifyEntity(ChildSampleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSampleEntity")
fun ChildSampleEntity(
  data: String,
  entitySource: EntitySource,
  init: (ChildSampleEntityBuilder.() -> Unit)? = null,
): ChildSampleEntityBuilder = ChildSampleEntityType(data, entitySource, init)
