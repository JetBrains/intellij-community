// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableModuleSourcePackagingElementEntity : ModifiableWorkspaceEntity<ModuleSourcePackagingElementEntity>, PackagingElementEntity.Builder<ModuleSourcePackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleSourcePackagingElementEntityType : EntityType<ModuleSourcePackagingElementEntity, ModifiableModuleSourcePackagingElementEntity>(
  PackagingElementEntityType) {
  override val entityClass: Class<ModuleSourcePackagingElementEntity> get() = ModuleSourcePackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableModuleSourcePackagingElementEntity.() -> Unit)? = null,
  ): ModifiableModuleSourcePackagingElementEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ModuleSourcePackagingElementEntity.Builder.() -> Unit)? = null,
  ): ModuleSourcePackagingElementEntity.Builder {
    val builder = builder() as ModuleSourcePackagingElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleSourcePackagingElementEntity(
  entity: ModuleSourcePackagingElementEntity,
  modification: ModifiableModuleSourcePackagingElementEntity.() -> Unit,
): ModuleSourcePackagingElementEntity = modifyEntity(ModifiableModuleSourcePackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleSourcePackagingElementEntity")
fun ModuleSourcePackagingElementEntity(
  entitySource: EntitySource,
  init: (ModifiableModuleSourcePackagingElementEntity.() -> Unit)? = null,
): ModifiableModuleSourcePackagingElementEntity = ModuleSourcePackagingElementEntityType(entitySource, init)
