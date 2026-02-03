// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AttachedEntityParentListModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface AttachedEntityParentListBuilder : WorkspaceEntityBuilder<AttachedEntityParentList> {
  override var entitySource: EntitySource
  var data: String
}

internal object AttachedEntityParentListType : EntityType<AttachedEntityParentList, AttachedEntityParentListBuilder>() {
  override val entityClass: Class<AttachedEntityParentList> get() = AttachedEntityParentList::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AttachedEntityParentListBuilder.() -> Unit)? = null,
  ): AttachedEntityParentListBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityParentList(
  entity: AttachedEntityParentList,
  modification: AttachedEntityParentListBuilder.() -> Unit,
): AttachedEntityParentList = modifyEntity(AttachedEntityParentListBuilder::class.java, entity, modification)

@Parent
var AttachedEntityParentListBuilder.ref: MainEntityParentListBuilder?
  by WorkspaceEntity.extensionBuilder(MainEntityParentList::class.java)


@JvmOverloads
@JvmName("createAttachedEntityParentList")
fun AttachedEntityParentList(
  data: String,
  entitySource: EntitySource,
  init: (AttachedEntityParentListBuilder.() -> Unit)? = null,
): AttachedEntityParentListBuilder = AttachedEntityParentListType(data, entitySource, init)
