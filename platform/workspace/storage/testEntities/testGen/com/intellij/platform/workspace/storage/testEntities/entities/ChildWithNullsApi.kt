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
interface ModifiableChildWithNulls : ModifiableWorkspaceEntity<ChildWithNulls> {
  override var entitySource: EntitySource
  var childData: String
}

internal object ChildWithNullsType : EntityType<ChildWithNulls, ModifiableChildWithNulls>() {
  override val entityClass: Class<ChildWithNulls> get() = ChildWithNulls::class.java
  operator fun invoke(
    childData: String,
    entitySource: EntitySource,
    init: (ModifiableChildWithNulls.() -> Unit)? = null,
  ): ModifiableChildWithNulls {
    val builder = builder()
    builder.childData = childData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildWithNulls(
  entity: ChildWithNulls,
  modification: ModifiableChildWithNulls.() -> Unit,
): ChildWithNulls = modifyEntity(ModifiableChildWithNulls::class.java, entity, modification)

@Parent
var ModifiableChildWithNulls.parentEntity: ModifiableParentWithNulls?
  by WorkspaceEntity.extensionBuilder(ParentWithNulls::class.java)


@JvmOverloads
@JvmName("createChildWithNulls")
fun ChildWithNulls(
  childData: String,
  entitySource: EntitySource,
  init: (ModifiableChildWithNulls.() -> Unit)? = null,
): ModifiableChildWithNulls = ChildWithNullsType(childData, entitySource, init)
