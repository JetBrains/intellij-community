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
interface ModifiableParentWithNullsMultiple : ModifiableWorkspaceEntity<ParentWithNullsMultiple> {
  override var entitySource: EntitySource
  var parentData: String
  var children: List<ModifiableChildWithNullsMultiple>
}

internal object ParentWithNullsMultipleType : EntityType<ParentWithNullsMultiple, ModifiableParentWithNullsMultiple>() {
  override val entityClass: Class<ParentWithNullsMultiple> get() = ParentWithNullsMultiple::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentWithNullsMultiple.() -> Unit)? = null,
  ): ModifiableParentWithNullsMultiple {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNullsMultiple(
  entity: ParentWithNullsMultiple,
  modification: ModifiableParentWithNullsMultiple.() -> Unit,
): ParentWithNullsMultiple = modifyEntity(ModifiableParentWithNullsMultiple::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithNullsMultiple")
fun ParentWithNullsMultiple(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentWithNullsMultiple.() -> Unit)? = null,
): ModifiableParentWithNullsMultiple = ParentWithNullsMultipleType(parentData, entitySource, init)
