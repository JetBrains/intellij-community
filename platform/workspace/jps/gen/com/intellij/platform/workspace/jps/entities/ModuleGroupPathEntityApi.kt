// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableModuleGroupPathEntity : ModifiableWorkspaceEntity<ModuleGroupPathEntity> {
  override var entitySource: EntitySource
  var module: ModifiableModuleEntity
  var path: MutableList<String>
}

internal object ModuleGroupPathEntityType : EntityType<ModuleGroupPathEntity, ModifiableModuleGroupPathEntity>() {
  override val entityClass: Class<ModuleGroupPathEntity> get() = ModuleGroupPathEntity::class.java
  operator fun invoke(
    path: List<String>,
    entitySource: EntitySource,
    init: (ModifiableModuleGroupPathEntity.() -> Unit)? = null,
  ): ModifiableModuleGroupPathEntity {
    val builder = builder()
    builder.path = path.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    path: List<String>,
    entitySource: EntitySource,
    init: (ModuleGroupPathEntity.Builder.() -> Unit)? = null,
  ): ModuleGroupPathEntity.Builder {
    val builder = builder() as ModuleGroupPathEntity.Builder
    builder.path = path.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyModuleGroupPathEntity(
  entity: ModuleGroupPathEntity,
  modification: ModifiableModuleGroupPathEntity.() -> Unit,
): ModuleGroupPathEntity = modifyEntity(ModifiableModuleGroupPathEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createModuleGroupPathEntity")
fun ModuleGroupPathEntity(
  path: List<String>,
  entitySource: EntitySource,
  init: (ModifiableModuleGroupPathEntity.() -> Unit)? = null,
): ModifiableModuleGroupPathEntity = ModuleGroupPathEntityType(path, entitySource, init)
