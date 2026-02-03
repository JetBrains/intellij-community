// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SubsetEnumEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SubsetEnumEntityBuilder : WorkspaceEntityBuilder<SubsetEnumEntity> {
  override var entitySource: EntitySource
  var someEnum: SubsetEnumEnum
}

internal object SubsetEnumEntityType : EntityType<SubsetEnumEntity, SubsetEnumEntityBuilder>() {
  override val entityClass: Class<SubsetEnumEntity> get() = SubsetEnumEntity::class.java
  operator fun invoke(
    someEnum: SubsetEnumEnum,
    entitySource: EntitySource,
    init: (SubsetEnumEntityBuilder.() -> Unit)? = null,
  ): SubsetEnumEntityBuilder {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySubsetEnumEntity(
  entity: SubsetEnumEntity,
  modification: SubsetEnumEntityBuilder.() -> Unit,
): SubsetEnumEntity = modifyEntity(SubsetEnumEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubsetEnumEntity")
fun SubsetEnumEntity(
  someEnum: SubsetEnumEnum,
  entitySource: EntitySource,
  init: (SubsetEnumEntityBuilder.() -> Unit)? = null,
): SubsetEnumEntityBuilder = SubsetEnumEntityType(someEnum, entitySource, init)
