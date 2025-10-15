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
interface ModifiableProjectModelTestEntity : ModifiableWorkspaceEntity<ProjectModelTestEntity> {
  override var entitySource: EntitySource
  var info: String
  var descriptor: Descriptor
  var parentEntity: ModifiableProjectModelTestEntity?
  var childrenEntities: List<ModifiableProjectModelTestEntity>
  var contentRoot: ModifiableContentRootTestEntity?
}

internal object ProjectModelTestEntityType : EntityType<ProjectModelTestEntity, ModifiableProjectModelTestEntity>() {
  override val entityClass: Class<ProjectModelTestEntity> get() = ProjectModelTestEntity::class.java
  operator fun invoke(
    info: String,
    descriptor: Descriptor,
    entitySource: EntitySource,
    init: (ModifiableProjectModelTestEntity.() -> Unit)? = null,
  ): ModifiableProjectModelTestEntity {
    val builder = builder()
    builder.info = info
    builder.descriptor = descriptor
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyProjectModelTestEntity(
  entity: ProjectModelTestEntity,
  modification: ModifiableProjectModelTestEntity.() -> Unit,
): ProjectModelTestEntity = modifyEntity(ModifiableProjectModelTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createProjectModelTestEntity")
fun ProjectModelTestEntity(
  info: String,
  descriptor: Descriptor,
  entitySource: EntitySource,
  init: (ModifiableProjectModelTestEntity.() -> Unit)? = null,
): ModifiableProjectModelTestEntity = ProjectModelTestEntityType(info, descriptor, entitySource, init)
