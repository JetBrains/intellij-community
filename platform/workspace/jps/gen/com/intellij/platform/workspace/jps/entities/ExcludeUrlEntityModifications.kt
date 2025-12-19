// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExcludeUrlEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ExcludeUrlEntityBuilder : WorkspaceEntityBuilder<ExcludeUrlEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
}

internal object ExcludeUrlEntityType : EntityType<ExcludeUrlEntity, ExcludeUrlEntityBuilder>() {
  override val entityClass: Class<ExcludeUrlEntity> get() = ExcludeUrlEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ExcludeUrlEntityBuilder.() -> Unit)? = null,
  ): ExcludeUrlEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ExcludeUrlEntity.Builder.() -> Unit)? = null,
  ): ExcludeUrlEntity.Builder {
    val builder = builder() as ExcludeUrlEntity.Builder
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyExcludeUrlEntity(
  entity: ExcludeUrlEntity,
  modification: ExcludeUrlEntityBuilder.() -> Unit,
): ExcludeUrlEntity = modifyEntity(ExcludeUrlEntityBuilder::class.java, entity, modification)

@Parent
var ExcludeUrlEntityBuilder.contentRoot: ContentRootEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ContentRootEntity::class.java)

@Parent
var ExcludeUrlEntityBuilder.library: LibraryEntityBuilder?
  by WorkspaceEntity.extensionBuilder(LibraryEntity::class.java)

@JvmOverloads
@JvmName("createExcludeUrlEntity")
fun ExcludeUrlEntity(
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ExcludeUrlEntityBuilder.() -> Unit)? = null,
): ExcludeUrlEntityBuilder = ExcludeUrlEntityType(url, entitySource, init)
