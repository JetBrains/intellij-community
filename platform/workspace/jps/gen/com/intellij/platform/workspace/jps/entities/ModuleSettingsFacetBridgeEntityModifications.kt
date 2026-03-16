// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleSettingsFacetBridgeEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ModuleSettingsFacetBridgeEntityBuilder<T : ModuleSettingsFacetBridgeEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var moduleId: ModuleId
  var name: String
}

internal object ModuleSettingsFacetBridgeEntityType :
  EntityType<ModuleSettingsFacetBridgeEntity, ModuleSettingsFacetBridgeEntityBuilder<ModuleSettingsFacetBridgeEntity>>() {
  override val entityClass: Class<ModuleSettingsFacetBridgeEntity> get() = ModuleSettingsFacetBridgeEntity::class.java
  operator fun invoke(
    moduleId: ModuleId,
    name: String,
    entitySource: EntitySource,
    init: (ModuleSettingsFacetBridgeEntityBuilder<ModuleSettingsFacetBridgeEntity>.() -> Unit)? = null,
  ): ModuleSettingsFacetBridgeEntityBuilder<ModuleSettingsFacetBridgeEntity> {
    val builder = builder()
    builder.moduleId = moduleId
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    moduleId: ModuleId,
    name: String,
    entitySource: EntitySource,
    init: (ModuleSettingsFacetBridgeEntity.Builder<ModuleSettingsFacetBridgeEntity>.() -> Unit)? = null,
  ): ModuleSettingsFacetBridgeEntity.Builder<ModuleSettingsFacetBridgeEntity> {
    val builder = builder() as ModuleSettingsFacetBridgeEntity.Builder<ModuleSettingsFacetBridgeEntity>
    builder.moduleId = moduleId
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
