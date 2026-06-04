// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XChildWithOptionalParentEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.XChildWithOptionalParentEntityImpl

@GeneratedCodeApiVersion(3)
interface XChildWithOptionalParentEntityBuilder : WorkspaceEntityBuilder<XChildWithOptionalParentEntity> {
  override var entitySource: EntitySource
  var childProperty: String
  var optionalParent: XParentEntityBuilder?
}

internal object XChildWithOptionalParentEntityType : EntityType<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityBuilder>() {
  override val entityClass: Class<XChildWithOptionalParentEntity> get() = XChildWithOptionalParentEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = XChildWithOptionalParentEntityImpl.Builder::class.java
  operator fun invoke(
    childProperty: String,
    entitySource: EntitySource,
    init: (XChildWithOptionalParentEntityBuilder.() -> Unit)? = null,
  ): XChildWithOptionalParentEntityBuilder {
    val builder = builder()
    builder.childProperty = childProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyXChildWithOptionalParentEntity(
  entity: XChildWithOptionalParentEntity,
  modification: XChildWithOptionalParentEntityBuilder.() -> Unit,
): XChildWithOptionalParentEntity = modifyEntity(XChildWithOptionalParentEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createXChildWithOptionalParentEntity")
fun XChildWithOptionalParentEntity(
  childProperty: String,
  entitySource: EntitySource,
  init: (XChildWithOptionalParentEntityBuilder.() -> Unit)? = null,
): XChildWithOptionalParentEntityBuilder = XChildWithOptionalParentEntityType(childProperty, entitySource, init)
