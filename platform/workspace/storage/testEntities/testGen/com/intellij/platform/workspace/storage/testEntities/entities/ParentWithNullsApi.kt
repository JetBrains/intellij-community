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
interface ModifiableParentWithNulls : ModifiableWorkspaceEntity<ParentWithNulls> {
  override var entitySource: EntitySource
  var parentData: String
  var child: ModifiableChildWithNulls?
}

internal object ParentWithNullsType : EntityType<ParentWithNulls, ModifiableParentWithNulls>() {
  override val entityClass: Class<ParentWithNulls> get() = ParentWithNulls::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentWithNulls.() -> Unit)? = null,
  ): ModifiableParentWithNulls {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNulls(
  entity: ParentWithNulls,
  modification: ModifiableParentWithNulls.() -> Unit,
): ParentWithNulls = modifyEntity(ModifiableParentWithNulls::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithNulls")
fun ParentWithNulls(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentWithNulls.() -> Unit)? = null,
): ModifiableParentWithNulls = ParentWithNullsType(parentData, entitySource, init)
