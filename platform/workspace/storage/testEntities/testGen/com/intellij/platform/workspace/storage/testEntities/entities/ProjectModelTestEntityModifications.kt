// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectModelTestEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ProjectModelTestEntityBuilder : WorkspaceEntityBuilder<ProjectModelTestEntity> {
  override var entitySource: EntitySource
  var info: String
  var descriptor: Descriptor
  var parentEntity: ProjectModelTestEntityBuilder?
  var childrenEntities: List<ProjectModelTestEntityBuilder>
  var contentRoot: ContentRootTestEntityBuilder?
}

internal object ProjectModelTestEntityType : EntityType<ProjectModelTestEntity, ProjectModelTestEntityBuilder>() {
  override val entityClass: Class<ProjectModelTestEntity> get() = ProjectModelTestEntity::class.java
  operator fun invoke(
    info: String,
    descriptor: Descriptor,
    entitySource: EntitySource,
    init: (ProjectModelTestEntityBuilder.() -> Unit)? = null,
  ): ProjectModelTestEntityBuilder {
    val builder = builder()
    builder.info = info
    builder.descriptor = descriptor
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyProjectModelTestEntity(
  entity: ProjectModelTestEntity,
  modification: ProjectModelTestEntityBuilder.() -> Unit,
): ProjectModelTestEntity = modifyEntity(ProjectModelTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createProjectModelTestEntity")
fun ProjectModelTestEntity(
  info: String,
  descriptor: Descriptor,
  entitySource: EntitySource,
  init: (ProjectModelTestEntityBuilder.() -> Unit)? = null,
): ProjectModelTestEntityBuilder = ProjectModelTestEntityType(info, descriptor, entitySource, init)
