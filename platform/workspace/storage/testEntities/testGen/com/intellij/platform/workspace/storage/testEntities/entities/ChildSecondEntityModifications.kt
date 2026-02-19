// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSecondEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildSecondEntityBuilder : WorkspaceEntityBuilder<ChildSecondEntity>, ChildAbstractBaseEntityBuilder<ChildSecondEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ParentAbEntityBuilder
  var secondData: String
}

internal object ChildSecondEntityType : EntityType<ChildSecondEntity, ChildSecondEntityBuilder>() {
  override val entityClass: Class<ChildSecondEntity> get() = ChildSecondEntity::class.java
  operator fun invoke(
    commonData: String,
    secondData: String,
    entitySource: EntitySource,
    init: (ChildSecondEntityBuilder.() -> Unit)? = null,
  ): ChildSecondEntityBuilder {
    val builder = builder()
    builder.commonData = commonData
    builder.secondData = secondData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSecondEntity(
  entity: ChildSecondEntity,
  modification: ChildSecondEntityBuilder.() -> Unit,
): ChildSecondEntity = modifyEntity(ChildSecondEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSecondEntity")
fun ChildSecondEntity(
  commonData: String,
  secondData: String,
  entitySource: EntitySource,
  init: (ChildSecondEntityBuilder.() -> Unit)? = null,
): ChildSecondEntityBuilder = ChildSecondEntityType(commonData, secondData, entitySource, init)
