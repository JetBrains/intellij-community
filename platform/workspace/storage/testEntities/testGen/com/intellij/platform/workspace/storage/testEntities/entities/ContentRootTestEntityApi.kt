// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableContentRootTestEntity : ModifiableWorkspaceEntity<ContentRootTestEntity> {
  override var entitySource: EntitySource
  var module: ModifiableModuleTestEntity
  var sourceRootOrder: ModifiableSourceRootTestOrderEntity?
  var sourceRoots: List<ModifiableSourceRootTestEntity>
}

internal object ContentRootTestEntityType : EntityType<ContentRootTestEntity, ModifiableContentRootTestEntity>() {
  override val entityClass: Class<ContentRootTestEntity> get() = ContentRootTestEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableContentRootTestEntity.() -> Unit)? = null,
  ): ModifiableContentRootTestEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyContentRootTestEntity(
  entity: ContentRootTestEntity,
  modification: ModifiableContentRootTestEntity.() -> Unit,
): ContentRootTestEntity = modifyEntity(ModifiableContentRootTestEntity::class.java, entity, modification)

@Parent
var ModifiableContentRootTestEntity.projectModelTestEntity: ModifiableProjectModelTestEntity?
  by WorkspaceEntity.extensionBuilder(ProjectModelTestEntity::class.java)


@JvmOverloads
@JvmName("createContentRootTestEntity")
fun ContentRootTestEntity(
  entitySource: EntitySource,
  init: (ModifiableContentRootTestEntity.() -> Unit)? = null,
): ModifiableContentRootTestEntity = ContentRootTestEntityType(entitySource, init)
