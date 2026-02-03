// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SpecificParentModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SpecificParentBuilder : WorkspaceEntityBuilder<SpecificParent>, AbstractParentEntityBuilder<SpecificParent> {
  override var entitySource: EntitySource
  override var data: String
  override var child: ChildWithExtensionParentBuilder?
}

internal object SpecificParentType : EntityType<SpecificParent, SpecificParentBuilder>() {
  override val entityClass: Class<SpecificParent> get() = SpecificParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SpecificParentBuilder.() -> Unit)? = null,
  ): SpecificParentBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificParent(
  entity: SpecificParent,
  modification: SpecificParentBuilder.() -> Unit,
): SpecificParent = modifyEntity(SpecificParentBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificParent")
fun SpecificParent(
  data: String,
  entitySource: EntitySource,
  init: (SpecificParentBuilder.() -> Unit)? = null,
): SpecificParentBuilder = SpecificParentType(data, entitySource, init)
