// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableProjectRootEntity : ModifiableWorkspaceEntity<ProjectRootEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object ProjectRootEntityType : EntityType<ProjectRootEntity, ModifiableProjectRootEntity>() {
  override val entityClass: Class<ProjectRootEntity> get() = ProjectRootEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableProjectRootEntity.() -> Unit)? = null,
  ): ModifiableProjectRootEntity {
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
  modification: ModifiableProjectRootEntity.() -> Unit,
): ProjectRootEntity = modifyEntity(ModifiableProjectRootEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createProjectRootEntity")
fun ProjectRootEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableProjectRootEntity.() -> Unit)? = null,
): ModifiableProjectRootEntity = ProjectRootEntityType(root, entitySource, init)
