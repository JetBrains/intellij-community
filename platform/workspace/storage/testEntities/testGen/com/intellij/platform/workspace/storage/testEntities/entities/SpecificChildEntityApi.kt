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
interface ModifiableSpecificChildEntity : ModifiableWorkspaceEntity<SpecificChildEntity>, ModifiableAbstractChildEntity<SpecificChildEntity> {
  override var entitySource: EntitySource
  override var data: String
  override var parent: ModifiableParentWithExtensionEntity
}

internal object SpecificChildEntityType : EntityType<SpecificChildEntity, ModifiableSpecificChildEntity>() {
  override val entityClass: Class<SpecificChildEntity> get() = SpecificChildEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSpecificChildEntity.() -> Unit)? = null,
  ): ModifiableSpecificChildEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySpecificChildEntity(
  entity: SpecificChildEntity,
  modification: ModifiableSpecificChildEntity.() -> Unit,
): SpecificChildEntity = modifyEntity(ModifiableSpecificChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSpecificChildEntity")
fun SpecificChildEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSpecificChildEntity.() -> Unit)? = null,
): ModifiableSpecificChildEntity = SpecificChildEntityType(data, entitySource, init)
