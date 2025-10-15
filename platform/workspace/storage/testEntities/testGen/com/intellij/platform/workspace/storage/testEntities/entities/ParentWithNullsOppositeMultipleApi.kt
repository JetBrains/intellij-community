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
interface ModifiableParentWithNullsOppositeMultiple : ModifiableWorkspaceEntity<ParentWithNullsOppositeMultiple> {
  override var entitySource: EntitySource
  var parentData: String
}

internal object ParentWithNullsOppositeMultipleType : EntityType<ParentWithNullsOppositeMultiple, ModifiableParentWithNullsOppositeMultiple>() {
  override val entityClass: Class<ParentWithNullsOppositeMultiple> get() = ParentWithNullsOppositeMultiple::class.java
  operator fun invoke(
    parentData: String,
    entitySource: EntitySource,
    init: (ModifiableParentWithNullsOppositeMultiple.() -> Unit)? = null,
  ): ModifiableParentWithNullsOppositeMultiple {
    val builder = builder()
    builder.parentData = parentData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithNullsOppositeMultiple(
  entity: ParentWithNullsOppositeMultiple,
  modification: ModifiableParentWithNullsOppositeMultiple.() -> Unit,
): ParentWithNullsOppositeMultiple = modifyEntity(ModifiableParentWithNullsOppositeMultiple::class.java, entity, modification)

var ModifiableParentWithNullsOppositeMultiple.children: List<ModifiableChildWithNullsOppositeMultiple>
  by WorkspaceEntity.extensionBuilder(ChildWithNullsOppositeMultiple::class.java)


@JvmOverloads
@JvmName("createParentWithNullsOppositeMultiple")
fun ParentWithNullsOppositeMultiple(
  parentData: String,
  entitySource: EntitySource,
  init: (ModifiableParentWithNullsOppositeMultiple.() -> Unit)? = null,
): ModifiableParentWithNullsOppositeMultiple = ParentWithNullsOppositeMultipleType(parentData, entitySource, init)
