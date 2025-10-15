// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableExcludeUrlEntity : ModifiableWorkspaceEntity<ExcludeUrlEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
}

internal object ExcludeUrlEntityType : EntityType<ExcludeUrlEntity, ModifiableExcludeUrlEntity>() {
  override val entityClass: Class<ExcludeUrlEntity> get() = ExcludeUrlEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableExcludeUrlEntity.() -> Unit)? = null,
  ): ModifiableExcludeUrlEntity {
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
  modification: ModifiableExcludeUrlEntity.() -> Unit,
): ExcludeUrlEntity = modifyEntity(ModifiableExcludeUrlEntity::class.java, entity, modification)

@Parent
var ModifiableExcludeUrlEntity.contentRoot: ModifiableContentRootEntity?
  by WorkspaceEntity.extensionBuilder(ContentRootEntity::class.java)

@Parent
var ModifiableExcludeUrlEntity.library: ModifiableLibraryEntity?
  by WorkspaceEntity.extensionBuilder(LibraryEntity::class.java)

@JvmOverloads
@JvmName("createExcludeUrlEntity")
fun ExcludeUrlEntity(
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableExcludeUrlEntity.() -> Unit)? = null,
): ModifiableExcludeUrlEntity = ExcludeUrlEntityType(url, entitySource, init)
