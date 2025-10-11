// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

/**
 * Provides connection between [ModuleEntity] and [GradleProjectEntity]
 */
interface GradleModuleEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity
  val gradleProjectId: GradleProjectEntityId

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleModuleEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var gradleProjectId: GradleProjectEntityId
  }

  companion object : EntityType<GradleModuleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      gradleProjectId: GradleProjectEntityId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.gradleProjectId = gradleProjectId
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyGradleModuleEntity(
  entity: GradleModuleEntity,
  modification: GradleModuleEntity.Builder.() -> Unit,
): GradleModuleEntity = modifyEntity(GradleModuleEntity.Builder::class.java, entity, modification)

var ModuleEntity.Builder.gradleModuleEntity: GradleModuleEntity.Builder?
  by WorkspaceEntity.extensionBuilder(GradleModuleEntity::class.java)
//endregion

val ModuleEntity.gradleModuleEntity: GradleModuleEntity?
  by WorkspaceEntity.extension()
