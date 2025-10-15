// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MainEntityParentListModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface MainEntityParentListBuilder : WorkspaceEntityBuilder<MainEntityParentList> {
  override var entitySource: EntitySource
  var x: String
  var children: List<AttachedEntityParentListBuilder>
}

internal object MainEntityParentListType : EntityType<MainEntityParentList, MainEntityParentListBuilder>() {
  override val entityClass: Class<MainEntityParentList> get() = MainEntityParentList::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (MainEntityParentListBuilder.() -> Unit)? = null,
  ): MainEntityParentListBuilder {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityParentList(
  entity: MainEntityParentList,
  modification: MainEntityParentListBuilder.() -> Unit,
): MainEntityParentList = modifyEntity(MainEntityParentListBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createMainEntityParentList")
fun MainEntityParentList(
  x: String,
  entitySource: EntitySource,
  init: (MainEntityParentListBuilder.() -> Unit)? = null,
): MainEntityParentListBuilder = MainEntityParentListType(x, entitySource, init)
