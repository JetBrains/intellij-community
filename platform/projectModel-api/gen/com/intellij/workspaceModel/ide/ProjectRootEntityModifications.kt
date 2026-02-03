// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectRootEntityModifications")

package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ProjectRootEntityBuilder : WorkspaceEntityBuilder<ProjectRootEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object ProjectRootEntityType : EntityType<ProjectRootEntity, ProjectRootEntityBuilder>() {
  override val entityClass: Class<ProjectRootEntity> get() = ProjectRootEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ProjectRootEntityBuilder.() -> Unit)? = null,
  ): ProjectRootEntityBuilder {
    val builder = builder()
    builder.root = root
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyProjectRootEntity(
  entity: ProjectRootEntity,
  modification: ProjectRootEntityBuilder.() -> Unit,
): ProjectRootEntity = modifyEntity(ProjectRootEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createProjectRootEntity")
fun ProjectRootEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ProjectRootEntityBuilder.() -> Unit)? = null,
): ProjectRootEntityBuilder = ProjectRootEntityType(root, entitySource, init)
