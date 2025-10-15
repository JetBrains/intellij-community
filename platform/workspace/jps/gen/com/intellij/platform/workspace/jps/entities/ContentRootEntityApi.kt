// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableContentRootEntity : ModifiableWorkspaceEntity<ContentRootEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var excludedPatterns: MutableList<String>
  var module: ModifiableModuleEntity
  var sourceRoots: List<ModifiableSourceRootEntity>
  var excludedUrls: List<ModifiableExcludeUrlEntity>
}

internal object ContentRootEntityType : EntityType<ContentRootEntity, ModifiableContentRootEntity>() {
  override val entityClass: Class<ContentRootEntity> get() = ContentRootEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    excludedPatterns: List<String>,
    entitySource: EntitySource,
    init: (ModifiableContentRootEntity.() -> Unit)? = null,
  ): ModifiableContentRootEntity {
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
  modification: ModifiableContentRootEntity.() -> Unit,
): ContentRootEntity = modifyEntity(ModifiableContentRootEntity::class.java, entity, modification)

@get:Internal
@set:Internal
var ModifiableContentRootEntity.excludeUrlOrder: ModifiableExcludeUrlOrderEntity?
  by WorkspaceEntity.extensionBuilder(ExcludeUrlOrderEntity::class.java)

@get:Internal
@set:Internal
var ModifiableContentRootEntity.sourceRootOrder: ModifiableSourceRootOrderEntity?
  by WorkspaceEntity.extensionBuilder(SourceRootOrderEntity::class.java)

@JvmOverloads
@JvmName("createContentRootEntity")
fun ContentRootEntity(
  url: VirtualFileUrl,
  excludedPatterns: List<String>,
  entitySource: EntitySource,
  init: (ModifiableContentRootEntity.() -> Unit)? = null,
): ModifiableContentRootEntity = ContentRootEntityType(url, excludedPatterns, entitySource, init)
