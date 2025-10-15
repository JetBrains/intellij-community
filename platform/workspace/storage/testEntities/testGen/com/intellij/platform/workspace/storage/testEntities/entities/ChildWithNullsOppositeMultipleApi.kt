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
interface ModifiableChildWithNullsOppositeMultiple : ModifiableWorkspaceEntity<ChildWithNullsOppositeMultiple> {
  override var entitySource: EntitySource
  var childData: String
  var parentEntity: ModifiableParentWithNullsOppositeMultiple?
}

internal object ChildWithNullsOppositeMultipleType : EntityType<ChildWithNullsOppositeMultiple, ModifiableChildWithNullsOppositeMultiple>() {
  override val entityClass: Class<ChildWithNullsOppositeMultiple> get() = ChildWithNullsOppositeMultiple::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildWithNullsOppositeMultiple.() -> Unit)? = null,
  ): ModifiableChildWithNullsOppositeMultiple {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithNullsOppositeMultiple(
  entity: ChildWithNullsOppositeMultiple,
  modification: ModifiableChildWithNullsOppositeMultiple.() -> Unit,
): ChildWithNullsOppositeMultiple = modifyEntity(ModifiableChildWithNullsOppositeMultiple::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildWithNullsOppositeMultiple")
fun ChildWithNullsOppositeMultiple(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildWithNullsOppositeMultiple.() -> Unit)? = null,
): ModifiableChildWithNullsOppositeMultiple = ChildWithNullsOppositeMultipleType(childData, entitySource, init)
