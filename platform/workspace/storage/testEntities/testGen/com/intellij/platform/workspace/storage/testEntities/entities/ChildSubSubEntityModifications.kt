// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSubSubEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildSubSubEntityBuilder : WorkspaceEntityBuilder<ChildSubSubEntity> {
  override var entitySource: EntitySource
  var parentEntity: ChildSubEntityBuilder
  var childData: String
}

internal object ChildSubSubEntityType : EntityType<ChildSubSubEntity, ChildSubSubEntityBuilder>() {
  override val entityClass: Class<ChildSubSubEntity> get() = ChildSubSubEntity::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ChildSubSubEntityBuilder.() -> Unit)? = null,
  ): ChildSubSubEntityBuilder {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSubSubEntity(
  entity: ChildSubSubEntity,
  modification: ChildSubSubEntityBuilder.() -> Unit,
): ChildSubSubEntity = modifyEntity(ChildSubSubEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSubSubEntity")
fun ChildSubSubEntity(
  childData: String,
  entitySource: EntitySource,
  init: (ChildSubSubEntityBuilder.() -> Unit)? = null,
): ChildSubSubEntityBuilder = ChildSubSubEntityType(childData, entitySource, init)
