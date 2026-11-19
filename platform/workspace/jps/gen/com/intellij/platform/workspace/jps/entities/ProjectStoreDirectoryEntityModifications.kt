// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectStoreDirectoryEntityModifications") @file:Internal

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.jps.entities.impl.ProjectStoreDirectoryEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ProjectStoreDirectoryEntityBuilder : WorkspaceEntityBuilder<ProjectStoreDirectoryEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
}

internal object ProjectStoreDirectoryEntityType : EntityType<ProjectStoreDirectoryEntity, ProjectStoreDirectoryEntityBuilder>() {
  override val entityImplClass: Class<*> get() = ProjectStoreDirectoryEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = ProjectStoreDirectoryEntityImpl.Builder::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ProjectStoreDirectoryEntityBuilder.() -> Unit)? = null,
  ): ProjectStoreDirectoryEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyProjectStoreDirectoryEntity(
  entity: ProjectStoreDirectoryEntity,
  modification: ProjectStoreDirectoryEntityBuilder.() -> Unit,
): ProjectStoreDirectoryEntity = modifyEntity(ProjectStoreDirectoryEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createProjectStoreDirectoryEntity")
fun ProjectStoreDirectoryEntity(
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ProjectStoreDirectoryEntityBuilder.() -> Unit)? = null,
): ProjectStoreDirectoryEntityBuilder = ProjectStoreDirectoryEntityType(url, entitySource, init)
