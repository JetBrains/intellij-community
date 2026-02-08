// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleProjectEntityModifications")

package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface GradleProjectEntityBuilder : WorkspaceEntityBuilder<GradleProjectEntity> {
  override var entitySource: EntitySource
  var build: GradleBuildEntityBuilder
  var buildId: GradleBuildEntityId
  var name: String
  var path: String
  var identityPath: String
  var url: VirtualFileUrl
  var linkedProjectId: String
}

internal object GradleProjectEntityType : EntityType<GradleProjectEntity, GradleProjectEntityBuilder>() {
  override val entityClass: Class<GradleProjectEntity> get() = GradleProjectEntity::class.java
  operator fun invoke(
    buildId: GradleBuildEntityId,
    name: String,
    path: String,
    identityPath: String,
    url: VirtualFileUrl,
    linkedProjectId: String,
    entitySource: EntitySource,
    init: (GradleProjectEntityBuilder.() -> Unit)? = null,
  ): GradleProjectEntityBuilder {
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
  modification: GradleProjectEntityBuilder.() -> Unit,
): GradleProjectEntity = modifyEntity(GradleProjectEntityBuilder::class.java, entity, modification)

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
  init: (GradleProjectEntityBuilder.() -> Unit)? = null,
): GradleProjectEntityBuilder = GradleProjectEntityType(buildId, name, path, identityPath, url, linkedProjectId, entitySource, init)
