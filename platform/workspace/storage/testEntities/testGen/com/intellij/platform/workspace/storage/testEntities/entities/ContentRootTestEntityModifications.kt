// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ContentRootTestEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ContentRootTestEntityBuilder : WorkspaceEntityBuilder<ContentRootTestEntity> {
  override var entitySource: EntitySource
  var module: ModuleTestEntityBuilder
  var sourceRootOrder: SourceRootTestOrderEntityBuilder?
  var sourceRoots: List<SourceRootTestEntityBuilder>
}

internal object ContentRootTestEntityType : EntityType<ContentRootTestEntity, ContentRootTestEntityBuilder>() {
  override val entityClass: Class<ContentRootTestEntity> get() = ContentRootTestEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ContentRootTestEntityBuilder.() -> Unit)? = null,
  ): ContentRootTestEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyContentRootTestEntity(
  entity: ContentRootTestEntity,
  modification: ContentRootTestEntityBuilder.() -> Unit,
): ContentRootTestEntity = modifyEntity(ContentRootTestEntityBuilder::class.java, entity, modification)

@Parent
var ContentRootTestEntityBuilder.projectModelTestEntity: ProjectModelTestEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ProjectModelTestEntity::class.java)


@JvmOverloads
@JvmName("createContentRootTestEntity")
fun ContentRootTestEntity(
  entitySource: EntitySource,
  init: (ContentRootTestEntityBuilder.() -> Unit)? = null,
): ContentRootTestEntityBuilder = ContentRootTestEntityType(entitySource, init)
