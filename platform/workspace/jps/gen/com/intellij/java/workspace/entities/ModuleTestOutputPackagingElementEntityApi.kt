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
interface ModifiableModuleTestOutputPackagingElementEntity : ModifiableWorkspaceEntity<ModuleTestOutputPackagingElementEntity>, PackagingElementEntity.Builder<ModuleTestOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleTestOutputPackagingElementEntityType : EntityType<ModuleTestOutputPackagingElementEntity, ModifiableModuleTestOutputPackagingElementEntity>(
  PackagingElementEntityType) {
  override val entityClass: Class<ModuleTestOutputPackagingElementEntity> get() = ModuleTestOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableModuleTestOutputPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableModuleTestOutputPackagingElementEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ModuleTestOutputPackagingElementEntity.Builder.() -> Unit)? = null,
  ): ModuleTestOutputPackagingElementEntity.Builder {
    val builder = builder() as ModuleTestOutputPackagingElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleTestOutputPackagingElementEntity(
  entity: ModuleTestOutputPackagingElementEntity,
  modification: ModifiableModuleTestOutputPackagingElementEntity.() -> Unit,
): ModuleTestOutputPackagingElementEntity = modifyEntity(ModifiableModuleTestOutputPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleTestOutputPackagingElementEntity")
fun ModuleTestOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModifiableModuleTestOutputPackagingElementEntity.() -> Unit)? = null,
): ModifiableModuleTestOutputPackagingElementEntity = ModuleTestOutputPackagingElementEntityType(entitySource, init)
