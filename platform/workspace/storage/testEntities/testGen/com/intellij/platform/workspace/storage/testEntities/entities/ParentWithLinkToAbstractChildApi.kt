// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableParentWithLinkToAbstractChild : ModifiableWorkspaceEntity<ParentWithLinkToAbstractChild> {
  override var entitySource: EntitySource
  var data: String
  var child: ModifiableAbstractChildWithLinkToParentEntity<out AbstractChildWithLinkToParentEntity>?
}

internal object ParentWithLinkToAbstractChildType : EntityType<ParentWithLinkToAbstractChild, ModifiableParentWithLinkToAbstractChild>() {
  override val entityClass: Class<ParentWithLinkToAbstractChild> get() = ParentWithLinkToAbstractChild::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableParentWithLinkToAbstractChild.() -> Unit)? = null,
  ): ModifiableParentWithLinkToAbstractChild {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithLinkToAbstractChild(
  entity: ParentWithLinkToAbstractChild,
  modification: ModifiableParentWithLinkToAbstractChild.() -> Unit,
): ParentWithLinkToAbstractChild = modifyEntity(ModifiableParentWithLinkToAbstractChild::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithLinkToAbstractChild")
fun ParentWithLinkToAbstractChild(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableParentWithLinkToAbstractChild.() -> Unit)? = null,
): ModifiableParentWithLinkToAbstractChild = ParentWithLinkToAbstractChildType(data, entitySource, init)
