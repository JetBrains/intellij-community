// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.externalSystem.impl.workspaceModel.ModifiableExternalProjectEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@GeneratedCodeApiVersion(3)
interface ModifiableGradleBuildEntity : ModifiableWorkspaceEntity<GradleBuildEntity> {
  override var entitySource: EntitySource
  var externalProject: ModifiableExternalProjectEntity
  var externalProjectId: ExternalProjectEntityId
  var name: String
  var url: VirtualFileUrl
}

internal object GradleBuildEntityType : EntityType<GradleBuildEntity, ModifiableGradleBuildEntity>() {
  override val entityClass: Class<GradleBuildEntity> get() = GradleBuildEntity::class.java
  operator fun invoke(
    externalProjectId: ExternalProjectEntityId,
    name: String,
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableGradleBuildEntity.() -> Unit)? = null,
  ): ModifiableGradleBuildEntity {
    val builder = builder()
    builder.externalProjectId = externalProjectId
    builder.name = name
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleBuildEntity(
  entity: GradleBuildEntity,
  modification: ModifiableGradleBuildEntity.() -> Unit,
): GradleBuildEntity = modifyEntity(ModifiableGradleBuildEntity::class.java, entity, modification)

var ModifiableExternalProjectEntity.gradleBuilds: List<ModifiableGradleBuildEntity>
  by WorkspaceEntity.extensionBuilder(GradleBuildEntity::class.java)


@JvmOverloads
@JvmName("createGradleBuildEntity")
fun GradleBuildEntity(
  externalProjectId: ExternalProjectEntityId,
  name: String,
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableGradleBuildEntity.() -> Unit)? = null,
): ModifiableGradleBuildEntity = GradleBuildEntityType(externalProjectId, name, url, entitySource, init)
