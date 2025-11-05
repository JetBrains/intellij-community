// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildWpidSampleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ChildWpidSampleEntityBuilder : WorkspaceEntityBuilder<ChildWpidSampleEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: SampleWithSymbolicIdEntityBuilder?
}

internal object ChildWpidSampleEntityType : EntityType<ChildWpidSampleEntity, ChildWpidSampleEntityBuilder>() {
  override val entityClass: Class<ChildWpidSampleEntity> get() = ChildWpidSampleEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ChildWpidSampleEntityBuilder.() -> Unit)? = null,
  ): ChildWpidSampleEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWpidSampleEntity(
  entity: ChildWpidSampleEntity,
  modification: ChildWpidSampleEntityBuilder.() -> Unit,
): ChildWpidSampleEntity = modifyEntity(ChildWpidSampleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildWpidSampleEntity")
fun ChildWpidSampleEntity(
  data: String,
  entitySource: EntitySource,
  init: (ChildWpidSampleEntityBuilder.() -> Unit)? = null,
): ChildWpidSampleEntityBuilder = ChildWpidSampleEntityType(data, entitySource, init)
