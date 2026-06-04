// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentEntityWithSymbolicIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.ParentEntityWithSymbolicIdImpl

@GeneratedCodeApiVersion(3)
interface ParentEntityWithSymbolicIdBuilder : WorkspaceEntityBuilder<ParentEntityWithSymbolicId> {
  override var entitySource: EntitySource
  var myName: String
  var children: List<ChildEntityWithSymbolicIdBuilder>
}

internal object ParentEntityWithSymbolicIdType : EntityType<ParentEntityWithSymbolicId, ParentEntityWithSymbolicIdBuilder>() {
  override val entityClass: Class<ParentEntityWithSymbolicId> get() = ParentEntityWithSymbolicId::class.java
  override val entityImplBuilderClass: Class<*> get() = ParentEntityWithSymbolicIdImpl.Builder::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (ParentEntityWithSymbolicIdBuilder.() -> Unit)? = null,
  ): ParentEntityWithSymbolicIdBuilder {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentEntityWithSymbolicId(
  entity: ParentEntityWithSymbolicId,
  modification: ParentEntityWithSymbolicIdBuilder.() -> Unit,
): ParentEntityWithSymbolicId = modifyEntity(ParentEntityWithSymbolicIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentEntityWithSymbolicId")
fun ParentEntityWithSymbolicId(
  myName: String,
  entitySource: EntitySource,
  init: (ParentEntityWithSymbolicIdBuilder.() -> Unit)? = null,
): ParentEntityWithSymbolicIdBuilder = ParentEntityWithSymbolicIdType(myName, entitySource, init)
