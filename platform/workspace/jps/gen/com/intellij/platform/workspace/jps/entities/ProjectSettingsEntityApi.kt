// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableProjectSettingsEntity : ModifiableWorkspaceEntity<ProjectSettingsEntity> {
  override var entitySource: EntitySource
  var projectSdk: SdkId?
}

internal object ProjectSettingsEntityType : EntityType<ProjectSettingsEntity, ModifiableProjectSettingsEntity>() {
  override val entityClass: Class<ProjectSettingsEntity> get() = ProjectSettingsEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableProjectSettingsEntity.() -> Unit)? = null,
  ): ModifiableProjectSettingsEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ProjectSettingsEntity.Builder.() -> Unit)? = null,
  ): ProjectSettingsEntity.Builder {
    val builder = builder() as ProjectSettingsEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyProjectSettingsEntity(
  entity: ProjectSettingsEntity,
  modification: ModifiableProjectSettingsEntity.() -> Unit,
): ProjectSettingsEntity = modifyEntity(ModifiableProjectSettingsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createProjectSettingsEntity")
fun ProjectSettingsEntity(
  entitySource: EntitySource,
  init: (ModifiableProjectSettingsEntity.() -> Unit)? = null,
): ModifiableProjectSettingsEntity = ProjectSettingsEntityType(entitySource, init)
