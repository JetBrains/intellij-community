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
interface ModifiableMainEntityParentList : ModifiableWorkspaceEntity<MainEntityParentList> {
  override var entitySource: EntitySource
  var x: String
  var children: List<ModifiableAttachedEntityParentList>
}

internal object MainEntityParentListType : EntityType<MainEntityParentList, ModifiableMainEntityParentList>() {
  override val entityClass: Class<MainEntityParentList> get() = MainEntityParentList::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (ModifiableMainEntityParentList.() -> Unit)? = null,
  ): ModifiableMainEntityParentList {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityParentList(
  entity: MainEntityParentList,
  modification: ModifiableMainEntityParentList.() -> Unit,
): MainEntityParentList = modifyEntity(ModifiableMainEntityParentList::class.java, entity, modification)

@JvmOverloads
@JvmName("createMainEntityParentList")
fun MainEntityParentList(
  x: String,
  entitySource: EntitySource,
  init: (ModifiableMainEntityParentList.() -> Unit)? = null,
): ModifiableMainEntityParentList = MainEntityParentListType(x, entitySource, init)
