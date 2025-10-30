// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ParentWithIdBuilder : WorkspaceEntityBuilder<ParentWithId> {
  override var entitySource: EntitySource
  var myId: String
  var parent: GrandParentWithIdBuilder
  var children: List<ChildWithIdBuilder>
}

internal object ParentWithIdType : EntityType<ParentWithId, ParentWithIdBuilder>() {
  override val entityClass: Class<ParentWithId> get() = ParentWithId::class.java
  operator fun invoke(
    myId: String,
    entitySource: EntitySource,
    init: (ParentWithIdBuilder.() -> Unit)? = null,
  ): ParentWithIdBuilder {
    val builder = builder()
    builder.myId = myId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithId(
  entity: ParentWithId,
  modification: ParentWithIdBuilder.() -> Unit,
): ParentWithId = modifyEntity(ParentWithIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithId")
fun ParentWithId(
  myId: String,
  entitySource: EntitySource,
  init: (ParentWithIdBuilder.() -> Unit)? = null,
): ParentWithIdBuilder = ParentWithIdType(myId, entitySource, init)
