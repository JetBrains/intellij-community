// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableGradleModuleEntity : ModifiableWorkspaceEntity<GradleModuleEntity> {
  override var entitySource: EntitySource
  var module: ModifiableModuleEntity
  var gradleProjectId: GradleProjectEntityId
}

internal object GradleModuleEntityType : EntityType<GradleModuleEntity, ModifiableGradleModuleEntity>() {
  override val entityClass: Class<GradleModuleEntity> get() = GradleModuleEntity::class.java
  operator fun invoke(
    gradleProjectId: GradleProjectEntityId,
    entitySource: EntitySource,
    init: (ModifiableGradleModuleEntity.() -> Unit)? = null,
  ): ModifiableGradleModuleEntity {
    val builder = builder()
    builder.gradleProjectId = gradleProjectId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleModuleEntity(
  entity: GradleModuleEntity,
  modification: ModifiableGradleModuleEntity.() -> Unit,
): GradleModuleEntity = modifyEntity(ModifiableGradleModuleEntity::class.java, entity, modification)

var ModifiableModuleEntity.gradleModuleEntity: ModifiableGradleModuleEntity?
  by WorkspaceEntity.extensionBuilder(GradleModuleEntity::class.java)


@JvmOverloads
@JvmName("createGradleModuleEntity")
fun GradleModuleEntity(
  gradleProjectId: GradleProjectEntityId,
  entitySource: EntitySource,
  init: (ModifiableGradleModuleEntity.() -> Unit)? = null,
): ModifiableGradleModuleEntity = GradleModuleEntityType(gradleProjectId, entitySource, init)
