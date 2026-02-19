// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceRootEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@GeneratedCodeApiVersion(3)
interface SourceRootEntityBuilder : WorkspaceEntityBuilder<SourceRootEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var rootTypeId: SourceRootTypeId
  var contentRoot: ContentRootEntityBuilder
}

internal object SourceRootEntityType : EntityType<SourceRootEntity, SourceRootEntityBuilder>() {
  override val entityClass: Class<SourceRootEntity> get() = SourceRootEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    rootTypeId: SourceRootTypeId,
    entitySource: EntitySource,
    init: (SourceRootEntityBuilder.() -> Unit)? = null,
  ): SourceRootEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.rootTypeId = rootTypeId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    url: VirtualFileUrl,
    rootTypeId: SourceRootTypeId,
    entitySource: EntitySource,
    init: (SourceRootEntity.Builder.() -> Unit)? = null,
  ): SourceRootEntity.Builder {
    val builder = builder() as SourceRootEntity.Builder
    builder.url = url
    builder.rootTypeId = rootTypeId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceRootEntity(
  entity: SourceRootEntity,
  modification: SourceRootEntityBuilder.() -> Unit,
): SourceRootEntity = modifyEntity(SourceRootEntityBuilder::class.java, entity, modification)

@get:Internal
@set:Internal
var SourceRootEntityBuilder.customSourceRootProperties: CustomSourceRootPropertiesEntityBuilder?
  by WorkspaceEntity.extensionBuilder(CustomSourceRootPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createSourceRootEntity")
fun SourceRootEntity(
  url: VirtualFileUrl,
  rootTypeId: SourceRootTypeId,
  entitySource: EntitySource,
  init: (SourceRootEntityBuilder.() -> Unit)? = null,
): SourceRootEntityBuilder = SourceRootEntityType(url, rootTypeId, entitySource, init)
