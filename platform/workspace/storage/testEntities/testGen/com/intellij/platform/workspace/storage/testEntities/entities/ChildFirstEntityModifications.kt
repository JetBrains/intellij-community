// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildFirstEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildFirstEntityBuilder : WorkspaceEntityBuilder<ChildFirstEntity>, ChildAbstractBaseEntityBuilder<ChildFirstEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ParentAbEntityBuilder
  var firstData: String
}

internal object ChildFirstEntityType : EntityType<ChildFirstEntity, ChildFirstEntityBuilder>() {
  override val entityClass: Class<ChildFirstEntity> get() = ChildFirstEntity::class.java
  operator fun invoke(
    commonData: String,
    firstData: String,
    entitySource: EntitySource,
    init: (ChildFirstEntityBuilder.() -> Unit)? = null,
  ): ChildFirstEntityBuilder {
    val builder = builder()
    builder.commonData = commonData
    builder.firstData = firstData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildFirstEntity(
  entity: ChildFirstEntity,
  modification: ChildFirstEntityBuilder.() -> Unit,
): ChildFirstEntity = modifyEntity(ChildFirstEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildFirstEntity")
fun ChildFirstEntity(
  commonData: String,
  firstData: String,
  entitySource: EntitySource,
  init: (ChildFirstEntityBuilder.() -> Unit)? = null,
): ChildFirstEntityBuilder = ChildFirstEntityType(commonData, firstData, entitySource, init)
