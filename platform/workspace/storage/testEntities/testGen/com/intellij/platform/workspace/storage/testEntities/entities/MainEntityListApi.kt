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
interface ModifiableMainEntityList : ModifiableWorkspaceEntity<MainEntityList> {
  override var entitySource: EntitySource
  var x: String
}

internal object MainEntityListType : EntityType<MainEntityList, ModifiableMainEntityList>() {
  override val entityClass: Class<MainEntityList> get() = MainEntityList::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (ModifiableMainEntityList.() -> Unit)? = null,
  ): ModifiableMainEntityList {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityList(
  entity: MainEntityList,
  modification: ModifiableMainEntityList.() -> Unit,
): MainEntityList = modifyEntity(ModifiableMainEntityList::class.java, entity, modification)

var ModifiableMainEntityList.child: List<ModifiableAttachedEntityList>
  by WorkspaceEntity.extensionBuilder(AttachedEntityList::class.java)


@JvmOverloads
@JvmName("createMainEntityList")
fun MainEntityList(
  x: String,
  entitySource: EntitySource,
  init: (ModifiableMainEntityList.() -> Unit)? = null,
): ModifiableMainEntityList = MainEntityListType(x, entitySource, init)
