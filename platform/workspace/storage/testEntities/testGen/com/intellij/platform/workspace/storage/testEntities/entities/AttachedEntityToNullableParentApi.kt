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
interface ModifiableAttachedEntityToNullableParent : ModifiableWorkspaceEntity<AttachedEntityToNullableParent> {
  override var entitySource: EntitySource
  var data: String
}

internal object AttachedEntityToNullableParentType : EntityType<AttachedEntityToNullableParent, ModifiableAttachedEntityToNullableParent>() {
  override val entityClass: Class<AttachedEntityToNullableParent> get() = AttachedEntityToNullableParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableAttachedEntityToNullableParent.() -> Unit)? = null,
  ): ModifiableAttachedEntityToNullableParent {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityToNullableParent(
  entity: AttachedEntityToNullableParent,
  modification: ModifiableAttachedEntityToNullableParent.() -> Unit,
): AttachedEntityToNullableParent = modifyEntity(ModifiableAttachedEntityToNullableParent::class.java, entity, modification)

@Parent
var ModifiableAttachedEntityToNullableParent.nullableRef: ModifiableMainEntityToParent?
  by WorkspaceEntity.extensionBuilder(MainEntityToParent::class.java)


@JvmOverloads
@JvmName("createAttachedEntityToNullableParent")
fun AttachedEntityToNullableParent(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableAttachedEntityToNullableParent.() -> Unit)? = null,
): ModifiableAttachedEntityToNullableParent = AttachedEntityToNullableParentType(data, entitySource, init)
