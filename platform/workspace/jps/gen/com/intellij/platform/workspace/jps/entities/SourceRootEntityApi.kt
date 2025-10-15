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
interface ModifiableSourceRootEntity : ModifiableWorkspaceEntity<SourceRootEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var rootTypeId: SourceRootTypeId
  var contentRoot: ModifiableContentRootEntity
}

internal object SourceRootEntityType : EntityType<SourceRootEntity, ModifiableSourceRootEntity>() {
  override val entityClass: Class<SourceRootEntity> get() = SourceRootEntity::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    rootTypeId: SourceRootTypeId,
    entitySource: EntitySource,
    init: (ModifiableSourceRootEntity.() -> Unit)? = null,
  ): ModifiableSourceRootEntity {
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
  modification: ModifiableSourceRootEntity.() -> Unit,
): SourceRootEntity = modifyEntity(ModifiableSourceRootEntity::class.java, entity, modification)

@get:Internal
@set:Internal
var ModifiableSourceRootEntity.customSourceRootProperties: ModifiableCustomSourceRootPropertiesEntity?
  by WorkspaceEntity.extensionBuilder(CustomSourceRootPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createSourceRootEntity")
fun SourceRootEntity(
  url: VirtualFileUrl,
  rootTypeId: SourceRootTypeId,
  entitySource: EntitySource,
  init: (ModifiableSourceRootEntity.() -> Unit)? = null,
): ModifiableSourceRootEntity = SourceRootEntityType(url, rootTypeId, entitySource, init)
