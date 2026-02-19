// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildWithExtensionParentModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ChildWithExtensionParentBuilder : WorkspaceEntityBuilder<ChildWithExtensionParent> {
  override var entitySource: EntitySource
  var data: String
}

internal object ChildWithExtensionParentType : EntityType<ChildWithExtensionParent, ChildWithExtensionParentBuilder>() {
  override val entityClass: Class<ChildWithExtensionParent> get() = ChildWithExtensionParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ChildWithExtensionParentBuilder.() -> Unit)? = null,
  ): ChildWithExtensionParentBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithExtensionParent(
  entity: ChildWithExtensionParent,
  modification: ChildWithExtensionParentBuilder.() -> Unit,
): ChildWithExtensionParent = modifyEntity(ChildWithExtensionParentBuilder::class.java, entity, modification)

@Parent
var ChildWithExtensionParentBuilder.parent: AbstractParentEntityBuilder<out AbstractParentEntity>?
  by WorkspaceEntity.extensionBuilder(AbstractParentEntity::class.java)


@JvmOverloads
@JvmName("createChildWithExtensionParent")
fun ChildWithExtensionParent(
  data: String,
  entitySource: EntitySource,
  init: (ChildWithExtensionParentBuilder.() -> Unit)? = null,
): ChildWithExtensionParentBuilder = ChildWithExtensionParentType(data, entitySource, init)
