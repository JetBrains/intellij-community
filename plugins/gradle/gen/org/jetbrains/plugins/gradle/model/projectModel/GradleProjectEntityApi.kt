// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@GeneratedCodeApiVersion(3)
interface ModifiableGradleProjectEntity : ModifiableWorkspaceEntity<GradleProjectEntity> {
  override var entitySource: EntitySource
  var buildId: GradleBuildEntityId
  var name: String
  var path: String
  var identityPath: String
  var url: VirtualFileUrl
  var linkedProjectId: String
}

internal object GradleProjectEntityType : EntityType<GradleProjectEntity, ModifiableGradleProjectEntity>() {
  override val entityClass: Class<GradleProjectEntity> get() = GradleProjectEntity::class.java
  operator fun invoke(
    buildId: GradleBuildEntityId,
    name: String,
    path: String,
    identityPath: String,
    url: VirtualFileUrl,
    linkedProjectId: String,
    entitySource: EntitySource,
    init: (ModifiableGradleProjectEntity.() -> Unit)? = null,
  ): ModifiableGradleProjectEntity {
    val builder = builder()
    builder.buildId = buildId
    builder.name = name
    builder.path = path
    builder.identityPath = identityPath
    builder.url = url
    builder.linkedProjectId = linkedProjectId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleProjectEntity(
  entity: GradleProjectEntity,
  modification: ModifiableGradleProjectEntity.() -> Unit,
): GradleProjectEntity = modifyEntity(ModifiableGradleProjectEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createGradleProjectEntity")
fun GradleProjectEntity(
  buildId: GradleBuildEntityId,
  name: String,
  path: String,
  identityPath: String,
  url: VirtualFileUrl,
  linkedProjectId: String,
  entitySource: EntitySource,
  init: (ModifiableGradleProjectEntity.() -> Unit)? = null,
): ModifiableGradleProjectEntity = GradleProjectEntityType(buildId, name, path, identityPath, url, linkedProjectId, entitySource, init)
