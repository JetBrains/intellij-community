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
interface ModifiableModuleOutputPackagingElementEntity : ModifiableWorkspaceEntity<ModuleOutputPackagingElementEntity>, PackagingElementEntity.Builder<ModuleOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleOutputPackagingElementEntityType : EntityType<ModuleOutputPackagingElementEntity, ModifiableModuleOutputPackagingElementEntity>(
  PackagingElementEntityType) {
  override val entityClass: Class<ModuleOutputPackagingElementEntity> get() = ModuleOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableModuleOutputPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableModuleOutputPackagingElementEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ModuleOutputPackagingElementEntity.Builder.() -> Unit)? = null,
  ): ModuleOutputPackagingElementEntity.Builder {
    val builder = builder() as ModuleOutputPackagingElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleOutputPackagingElementEntity(
  entity: ModuleOutputPackagingElementEntity,
  modification: ModifiableModuleOutputPackagingElementEntity.() -> Unit,
): ModuleOutputPackagingElementEntity = modifyEntity(ModifiableModuleOutputPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleOutputPackagingElementEntity")
fun ModuleOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModifiableModuleOutputPackagingElementEntity.() -> Unit)? = null,
): ModifiableModuleOutputPackagingElementEntity = ModuleOutputPackagingElementEntityType(entitySource, init)
