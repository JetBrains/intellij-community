// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildEntityWithSymbolicIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildEntityWithSymbolicIdImpl

@GeneratedCodeApiVersion(3)
interface ChildEntityWithSymbolicIdBuilder : WorkspaceEntityBuilder<ChildEntityWithSymbolicId> {
  override var entitySource: EntitySource
  var myName: String
  var parent: ParentEntityWithSymbolicIdBuilder
}

internal object ChildEntityWithSymbolicIdType : EntityType<ChildEntityWithSymbolicId, ChildEntityWithSymbolicIdBuilder>() {
  override val entityClass: Class<ChildEntityWithSymbolicId> get() = ChildEntityWithSymbolicId::class.java
  override val entityImplBuilderClass: Class<*> get() = ChildEntityWithSymbolicIdImpl.Builder::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (ChildEntityWithSymbolicIdBuilder.() -> Unit)? = null,
  ): ChildEntityWithSymbolicIdBuilder {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntityWithSymbolicId(
  entity: ChildEntityWithSymbolicId,
  modification: ChildEntityWithSymbolicIdBuilder.() -> Unit,
): ChildEntityWithSymbolicId = modifyEntity(ChildEntityWithSymbolicIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntityWithSymbolicId")
fun ChildEntityWithSymbolicId(
  myName: String,
  entitySource: EntitySource,
  init: (ChildEntityWithSymbolicIdBuilder.() -> Unit)? = null,
): ChildEntityWithSymbolicIdBuilder = ChildEntityWithSymbolicIdType(myName, entitySource, init)
