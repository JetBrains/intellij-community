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
interface ModifiableAttachedEntityToParent : ModifiableWorkspaceEntity<AttachedEntityToParent> {
  override var entitySource: EntitySource
  var data: String
}

internal object AttachedEntityToParentType : EntityType<AttachedEntityToParent, ModifiableAttachedEntityToParent>() {
  override val entityClass: Class<AttachedEntityToParent> get() = AttachedEntityToParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAttachedEntityToParent.() -> Unit)? = null,
  ): ModifiableAttachedEntityToParent {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityToParent(
  entity: AttachedEntityToParent,
  modification: ModifiableAttachedEntityToParent.() -> Unit,
): AttachedEntityToParent = modifyEntity(ModifiableAttachedEntityToParent::class.java, entity, modification)

@Parent
var ModifiableAttachedEntityToParent.ref: ModifiableMainEntityToParent
  by WorkspaceEntity.extensionBuilder(MainEntityToParent::class.java)


@JvmOverloads
@JvmName("createAttachedEntityToParent")
fun AttachedEntityToParent(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableAttachedEntityToParent.() -> Unit)? = null,
): ModifiableAttachedEntityToParent = AttachedEntityToParentType(data, entitySource, init)
