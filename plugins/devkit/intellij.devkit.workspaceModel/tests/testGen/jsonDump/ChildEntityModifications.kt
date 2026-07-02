// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildEntityModifications")

package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.devkit.workspaceModel.jsonDump.impl.ChildEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ChildEntityBuilder : WorkspaceEntityBuilder<ChildEntity> {
  override var entitySource: EntitySource
  var childName: String
  var parent: BaseTestEntityBuilder
}

internal object ChildEntityType : EntityType<ChildEntity, ChildEntityBuilder>() {
  override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = ChildEntityImpl.Builder::class.java
  operator fun invoke(
    childName: String,
    entitySource: EntitySource,
    init: (ChildEntityBuilder.() -> Unit)? = null,
  ): ChildEntityBuilder {
    val builder = builder()
    builder.childName = childName
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
  childName: String,
  entitySource: EntitySource,
  init: (ChildEntityBuilder.() -> Unit)? = null,
): ChildEntityBuilder = ChildEntityType(childName, entitySource, init)
