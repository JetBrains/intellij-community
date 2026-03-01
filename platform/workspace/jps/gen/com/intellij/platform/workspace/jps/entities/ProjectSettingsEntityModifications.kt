// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectSettingsEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ProjectSettingsEntityBuilder : WorkspaceEntityBuilder<ProjectSettingsEntity> {
  override var entitySource: EntitySource
  var projectSdk: SdkId?
}

internal object ProjectSettingsEntityType : EntityType<ProjectSettingsEntity, ProjectSettingsEntityBuilder>() {
  override val entityClass: Class<ProjectSettingsEntity> get() = ProjectSettingsEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ProjectSettingsEntityBuilder.() -> Unit)? = null,
  ): ProjectSettingsEntityBuilder {
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
  modification: ProjectSettingsEntityBuilder.() -> Unit,
): ProjectSettingsEntity = modifyEntity(ProjectSettingsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createProjectSettingsEntity")
fun ProjectSettingsEntity(
  entitySource: EntitySource,
  init: (ProjectSettingsEntityBuilder.() -> Unit)? = null,
): ProjectSettingsEntityBuilder = ProjectSettingsEntityType(entitySource, init)
