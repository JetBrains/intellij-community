// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleModuleEntityModifications")

package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface GradleModuleEntityBuilder : WorkspaceEntityBuilder<GradleModuleEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var gradleProjectId: GradleProjectEntityId
}

internal object GradleModuleEntityType : EntityType<GradleModuleEntity, GradleModuleEntityBuilder>() {
  override val entityClass: Class<GradleModuleEntity> get() = GradleModuleEntity::class.java
  operator fun invoke(
    gradleProjectId: GradleProjectEntityId,
    entitySource: EntitySource,
    init: (GradleModuleEntityBuilder.() -> Unit)? = null,
  ): GradleModuleEntityBuilder {
    val builder = builder()
    builder.gradleProjectId = gradleProjectId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleModuleEntity(
  entity: GradleModuleEntity,
  modification: GradleModuleEntityBuilder.() -> Unit,
): GradleModuleEntity = modifyEntity(GradleModuleEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.gradleModuleEntity: GradleModuleEntityBuilder?
  by WorkspaceEntity.extensionBuilder(GradleModuleEntity::class.java)


@JvmOverloads
@JvmName("createGradleModuleEntity")
fun GradleModuleEntity(
  gradleProjectId: GradleProjectEntityId,
  entitySource: EntitySource,
  init: (GradleModuleEntityBuilder.() -> Unit)? = null,
): GradleModuleEntityBuilder = GradleModuleEntityType(gradleProjectId, entitySource, init)
