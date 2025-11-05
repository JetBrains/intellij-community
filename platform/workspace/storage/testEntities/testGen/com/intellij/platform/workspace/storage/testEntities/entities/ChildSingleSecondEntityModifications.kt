// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSingleSecondEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildSingleSecondEntityBuilder : WorkspaceEntityBuilder<ChildSingleSecondEntity>, ChildSingleAbstractBaseEntityBuilder<ChildSingleSecondEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ParentSingleAbEntityBuilder
  var secondData: String
}

internal object ChildSingleSecondEntityType : EntityType<ChildSingleSecondEntity, ChildSingleSecondEntityBuilder>() {
  override val entityClass: Class<ChildSingleSecondEntity> get() = ChildSingleSecondEntity::class.java
  operator fun invoke(
    commonData: String,
    secondData: String,
    entitySource: EntitySource,
    init: (ChildSingleSecondEntityBuilder.() -> Unit)? = null,
  ): ChildSingleSecondEntityBuilder {
    val builder = builder()
    builder.commonData = commonData
    builder.secondData = secondData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSingleSecondEntity(
  entity: ChildSingleSecondEntity,
  modification: ChildSingleSecondEntityBuilder.() -> Unit,
): ChildSingleSecondEntity = modifyEntity(ChildSingleSecondEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSingleSecondEntity")
fun ChildSingleSecondEntity(
  commonData: String,
  secondData: String,
  entitySource: EntitySource,
  init: (ChildSingleSecondEntityBuilder.() -> Unit)? = null,
): ChildSingleSecondEntityBuilder = ChildSingleSecondEntityType(commonData, secondData, entitySource, init)
