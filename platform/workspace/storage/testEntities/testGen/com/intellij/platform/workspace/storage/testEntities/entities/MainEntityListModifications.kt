// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MainEntityListModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface MainEntityListBuilder : WorkspaceEntityBuilder<MainEntityList> {
  override var entitySource: EntitySource
  var x: String
}

internal object MainEntityListType : EntityType<MainEntityList, MainEntityListBuilder>() {
  override val entityClass: Class<MainEntityList> get() = MainEntityList::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (MainEntityListBuilder.() -> Unit)? = null,
  ): MainEntityListBuilder {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityList(
  entity: MainEntityList,
  modification: MainEntityListBuilder.() -> Unit,
): MainEntityList = modifyEntity(MainEntityListBuilder::class.java, entity, modification)

var MainEntityListBuilder.child: List<AttachedEntityListBuilder>
  by WorkspaceEntity.extensionBuilder(AttachedEntityList::class.java)


@JvmOverloads
@JvmName("createMainEntityList")
fun MainEntityList(
  x: String,
  entitySource: EntitySource,
  init: (MainEntityListBuilder.() -> Unit)? = null,
): MainEntityListBuilder = MainEntityListType(x, entitySource, init)
