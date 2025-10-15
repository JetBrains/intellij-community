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
interface ModifiableChildWithNullsMultiple : ModifiableWorkspaceEntity<ChildWithNullsMultiple> {
  override var entitySource: EntitySource
  var childData: String
}

internal object ChildWithNullsMultipleType : EntityType<ChildWithNullsMultiple, ModifiableChildWithNullsMultiple>() {
  override val entityClass: Class<ChildWithNullsMultiple> get() = ChildWithNullsMultiple::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildWithNullsMultiple.() -> Unit)? = null,
  ): ModifiableChildWithNullsMultiple {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithNullsMultiple(
  entity: ChildWithNullsMultiple,
  modification: ModifiableChildWithNullsMultiple.() -> Unit,
): ChildWithNullsMultiple = modifyEntity(ModifiableChildWithNullsMultiple::class.java, entity, modification)

@Parent
var ModifiableChildWithNullsMultiple.parent: ModifiableParentWithNullsMultiple?
  by WorkspaceEntity.extensionBuilder(ParentWithNullsMultiple::class.java)


@JvmOverloads
@JvmName("createChildWithNullsMultiple")
fun ChildWithNullsMultiple(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildWithNullsMultiple.() -> Unit)? = null,
): ModifiableChildWithNullsMultiple = ChildWithNullsMultipleType(childData, entitySource, init)
