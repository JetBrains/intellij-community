// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableAttachedEntityList : ModifiableWorkspaceEntity<AttachedEntityList> {
  override var entitySource: EntitySource
  var ref: ModifiableMainEntityList?
  var data: String
}

internal object AttachedEntityListType : EntityType<AttachedEntityList, ModifiableAttachedEntityList>() {
  override val entityClass: Class<AttachedEntityList> get() = AttachedEntityList::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAttachedEntityList.() -> Unit)? = null,
  ): ModifiableAttachedEntityList {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityList(
  entity: AttachedEntityList,
  modification: ModifiableAttachedEntityList.() -> Unit,
): AttachedEntityList = modifyEntity(ModifiableAttachedEntityList::class.java, entity, modification)

@JvmOverloads
@JvmName("createAttachedEntityList")
fun AttachedEntityList(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableAttachedEntityList.() -> Unit)? = null,
): ModifiableAttachedEntityList = AttachedEntityListType(data, entitySource, init)
