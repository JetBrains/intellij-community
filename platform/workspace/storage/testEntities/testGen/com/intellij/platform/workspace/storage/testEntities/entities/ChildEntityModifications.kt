// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildEntityBuilder : WorkspaceEntityBuilder<ChildEntity> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ParentEntityBuilder
}

internal object ChildEntityType : EntityType<ChildEntity, ChildEntityBuilder>() {
  override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ChildEntityBuilder.() -> Unit)? = null,
  ): ChildEntityBuilder {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntity(
  entity: ChildEntity,
  modification: ChildEntityBuilder.() -> Unit,
): ChildEntity = modifyEntity(ChildEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntity")
fun ChildEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder = ChildEntityType(childData, entitySource, init)
