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
interface ModifiableSpecificParent : ModifiableWorkspaceEntity<SpecificParent>, ModifiableAbstractParentEntity<SpecificParent> {
  override var entitySource: EntitySource
  override var data: String
  override var child: ModifiableChildWithExtensionParent?
}

internal object SpecificParentType : EntityType<SpecificParent, ModifiableSpecificParent>() {
  override val entityClass: Class<SpecificParent> get() = SpecificParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSpecificParent.() -> Unit)? = null,
  ): ModifiableSpecificParent {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificParent(
  entity: SpecificParent,
  modification: ModifiableSpecificParent.() -> Unit,
): SpecificParent = modifyEntity(ModifiableSpecificParent::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificParent")
fun SpecificParent(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSpecificParent.() -> Unit)? = null,
): ModifiableSpecificParent = SpecificParentType(data, entitySource, init)
