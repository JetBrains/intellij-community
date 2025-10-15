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
interface ModifiableModuleCustomImlDataEntity : ModifiableWorkspaceEntity<ModuleCustomImlDataEntity> {
  override var entitySource: EntitySource
  var rootManagerTagCustomData: String?
  var customModuleOptions: Map<String, String>
  var module: ModifiableModuleEntity
}

internal object ModuleCustomImlDataEntityType : EntityType<ModuleCustomImlDataEntity, ModifiableModuleCustomImlDataEntity>() {
  override val entityClass: Class<ModuleCustomImlDataEntity> get() = ModuleCustomImlDataEntity::class.java
  operator fun invoke(
    customModuleOptions: Map<String, String>,
    entitySource: EntitySource,
    init: (ModifiableModuleCustomImlDataEntity.() -> Unit)? = null,
  ): ModifiableModuleCustomImlDataEntity {
    val builder = builder()
    builder.customModuleOptions = customModuleOptions
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    customModuleOptions: Map<String, String>,
    entitySource: EntitySource,
    init: (ModuleCustomImlDataEntity.Builder.() -> Unit)? = null,
  ): ModuleCustomImlDataEntity.Builder {
    val builder = builder() as ModuleCustomImlDataEntity.Builder
    builder.customModuleOptions = customModuleOptions
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyModuleCustomImlDataEntity(
  entity: ModuleCustomImlDataEntity,
  modification: ModifiableModuleCustomImlDataEntity.() -> Unit,
): ModuleCustomImlDataEntity = modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createModuleCustomImlDataEntity")
fun ModuleCustomImlDataEntity(
  customModuleOptions: Map<String, String>,
  entitySource: EntitySource,
  init: (ModifiableModuleCustomImlDataEntity.() -> Unit)? = null,
): ModifiableModuleCustomImlDataEntity = ModuleCustomImlDataEntityType(customModuleOptions, entitySource, init)
