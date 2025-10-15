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
interface ModifiableSpecificChildWithLinkToParentEntity : ModifiableWorkspaceEntity<SpecificChildWithLinkToParentEntity>, ModifiableAbstractChildWithLinkToParentEntity<SpecificChildWithLinkToParentEntity> {
  override var entitySource: EntitySource
  override var data: String
}

internal object SpecificChildWithLinkToParentEntityType : EntityType<SpecificChildWithLinkToParentEntity, ModifiableSpecificChildWithLinkToParentEntity>() {
  override val entityClass: Class<SpecificChildWithLinkToParentEntity> get() = SpecificChildWithLinkToParentEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSpecificChildWithLinkToParentEntity.() -> Unit)? = null,
  ): ModifiableSpecificChildWithLinkToParentEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificChildWithLinkToParentEntity(
  entity: SpecificChildWithLinkToParentEntity,
  modification: ModifiableSpecificChildWithLinkToParentEntity.() -> Unit,
): SpecificChildWithLinkToParentEntity = modifyEntity(ModifiableSpecificChildWithLinkToParentEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificChildWithLinkToParentEntity")
fun SpecificChildWithLinkToParentEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSpecificChildWithLinkToParentEntity.() -> Unit)? = null,
): ModifiableSpecificChildWithLinkToParentEntity = SpecificChildWithLinkToParentEntityType(data, entitySource, init)
