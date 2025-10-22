// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ContentRootEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@GeneratedCodeApiVersion(3)
interface ContentRootEntityBuilder : WorkspaceEntityBuilder<ContentRootEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var excludedPatterns: MutableList<String>
  var module: ModuleEntityBuilder
  var sourceRoots: List<SourceRootEntityBuilder>
  var excludedUrls: List<ExcludeUrlEntityBuilder>
}

internal object ContentRootEntityType : EntityType<ContentRootEntity, ContentRootEntityBuilder>() {
  override val entityClass: Class<ContentRootEntity> get() = ContentRootEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    excludedPatterns: List<String>,
    entitySource: EntitySource,
    init: (ContentRootEntityBuilder.() -> Unit)? = null,
  ): ContentRootEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.excludedPatterns = excludedPatterns.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    url: VirtualFileUrl,
    excludedPatterns: List<String>,
    entitySource: EntitySource,
    init: (ContentRootEntity.Builder.() -> Unit)? = null,
  ): ContentRootEntity.Builder {
    val builder = builder() as ContentRootEntity.Builder
    builder.url = url
    builder.excludedPatterns = excludedPatterns.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyContentRootEntity(
  entity: ContentRootEntity,
  modification: ContentRootEntityBuilder.() -> Unit,
): ContentRootEntity = modifyEntity(ContentRootEntityBuilder::class.java, entity, modification)

@get:Internal
@set:Internal
var ContentRootEntityBuilder.excludeUrlOrder: ExcludeUrlOrderEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ExcludeUrlOrderEntity::class.java)

@get:Internal
@set:Internal
var ContentRootEntityBuilder.sourceRootOrder: SourceRootOrderEntityBuilder?
  by WorkspaceEntity.extensionBuilder(SourceRootOrderEntity::class.java)

@JvmOverloads
@JvmName("createContentRootEntity")
fun ContentRootEntity(
  url: VirtualFileUrl,
  excludedPatterns: List<String>,
  entitySource: EntitySource,
  init: (ContentRootEntityBuilder.() -> Unit)? = null,
): ContentRootEntityBuilder = ContentRootEntityType(url, excludedPatterns, entitySource, init)
