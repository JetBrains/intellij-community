// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildWithIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.ChildWithIdImpl

@GeneratedCodeApiVersion(3)
interface ChildWithIdBuilder : WorkspaceEntityBuilder<ChildWithId> {
  override var entitySource: EntitySource
  var myId: String
  var parent: ParentWithIdBuilder
}

internal object ChildWithIdType : EntityType<ChildWithId, ChildWithIdBuilder>() {
  override val entityClass: Class<ChildWithId> get() = ChildWithId::class.java
  override val entityImplBuilderClass: Class<*> get() = ChildWithIdImpl.Builder::class.java
  operator fun invoke(
    myId: String,
    entitySource: EntitySource,
    init: (ChildWithIdBuilder.() -> Unit)? = null,
  ): ChildWithIdBuilder {
    val builder = builder()
    builder.myId = myId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithId(
  entity: ChildWithId,
  modification: ChildWithIdBuilder.() -> Unit,
): ChildWithId = modifyEntity(ChildWithIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildWithId")
fun ChildWithId(
  myId: String,
  entitySource: EntitySource,
  init: (ChildWithIdBuilder.() -> Unit)? = null,
): ChildWithIdBuilder = ChildWithIdType(myId, entitySource, init)
