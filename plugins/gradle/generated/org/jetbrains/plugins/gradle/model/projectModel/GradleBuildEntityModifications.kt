// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleBuildEntityModifications")

package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@GeneratedCodeApiVersion(3)
interface GradleBuildEntityBuilder : WorkspaceEntityBuilder<GradleBuildEntity> {
  override var entitySource: EntitySource
  var externalProject: ExternalProjectEntityBuilder
  var externalProjectId: ExternalProjectEntityId
  var name: String
  var url: VirtualFileUrl
  var projects: List<GradleProjectEntityBuilder>
}

internal object GradleBuildEntityType : EntityType<GradleBuildEntity, GradleBuildEntityBuilder>() {
  override val entityClass: Class<GradleBuildEntity> get() = GradleBuildEntity::class.java
  operator fun invoke(
    externalProjectId: ExternalProjectEntityId,
    name: String,
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (GradleBuildEntityBuilder.() -> Unit)? = null,
  ): GradleBuildEntityBuilder {
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
  modification: GradleBuildEntityBuilder.() -> Unit,
): GradleBuildEntity = modifyEntity(GradleBuildEntityBuilder::class.java, entity, modification)

var ExternalProjectEntityBuilder.gradleBuilds: List<GradleBuildEntityBuilder>
  by WorkspaceEntity.extensionBuilder(GradleBuildEntity::class.java)


@JvmOverloads
@JvmName("createGradleBuildEntity")
fun GradleBuildEntity(
  externalProjectId: ExternalProjectEntityId,
  name: String,
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (GradleBuildEntityBuilder.() -> Unit)? = null,
): GradleBuildEntityBuilder = GradleBuildEntityType(externalProjectId, name, url, entitySource, init)
