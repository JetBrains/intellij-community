// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SecondSampleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SecondSampleEntityBuilder : WorkspaceEntityBuilder<SecondSampleEntity> {
  override var entitySource: EntitySource
  var intProperty: Int
}

internal object SecondSampleEntityType : EntityType<SecondSampleEntity, SecondSampleEntityBuilder>() {
  override val entityClass: Class<SecondSampleEntity> get() = SecondSampleEntity::class.java
  operator fun invoke(
    intProperty: Int,
    entitySource: EntitySource,
    init: (SecondSampleEntityBuilder.() -> Unit)? = null,
  ): SecondSampleEntityBuilder {
    val builder = builder()
    builder.intProperty = intProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySecondSampleEntity(
  entity: SecondSampleEntity,
  modification: SecondSampleEntityBuilder.() -> Unit,
): SecondSampleEntity = modifyEntity(SecondSampleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSecondSampleEntity")
fun SecondSampleEntity(
  intProperty: Int,
  entitySource: EntitySource,
  init: (SecondSampleEntityBuilder.() -> Unit)? = null,
): SecondSampleEntityBuilder = SecondSampleEntityType(intProperty, entitySource, init)
