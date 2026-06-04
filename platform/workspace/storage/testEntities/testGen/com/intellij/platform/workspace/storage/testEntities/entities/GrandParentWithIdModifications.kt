// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GrandParentWithIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.GrandParentWithIdImpl

@GeneratedCodeApiVersion(3)
interface GrandParentWithIdBuilder : WorkspaceEntityBuilder<GrandParentWithId> {
  override var entitySource: EntitySource
  var myId: String
}

internal object GrandParentWithIdType : EntityType<GrandParentWithId, GrandParentWithIdBuilder>() {
  override val entityClass: Class<GrandParentWithId> get() = GrandParentWithId::class.java
  override val entityImplBuilderClass: Class<*> get() = GrandParentWithIdImpl.Builder::class.java
  operator fun invoke(
    myId: String,
    entitySource: EntitySource,
    init: (GrandParentWithIdBuilder.() -> Unit)? = null,
  ): GrandParentWithIdBuilder {
    val builder = builder()
    builder.myId = myId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGrandParentWithId(
  entity: GrandParentWithId,
  modification: GrandParentWithIdBuilder.() -> Unit,
): GrandParentWithId = modifyEntity(GrandParentWithIdBuilder::class.java, entity, modification)

var GrandParentWithIdBuilder.children: List<ParentWithIdBuilder>
  by WorkspaceEntity.extensionBuilder(ParentWithId::class.java)


@JvmOverloads
@JvmName("createGrandParentWithId")
fun GrandParentWithId(
  myId: String,
  entitySource: EntitySource,
  init: (GrandParentWithIdBuilder.() -> Unit)? = null,
): GrandParentWithIdBuilder = GrandParentWithIdType(myId, entitySource, init)
