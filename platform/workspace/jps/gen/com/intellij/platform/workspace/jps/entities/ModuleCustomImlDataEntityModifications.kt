// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleCustomImlDataEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModuleCustomImlDataEntityBuilder : WorkspaceEntityBuilder<ModuleCustomImlDataEntity> {
  override var entitySource: EntitySource
  var rootManagerTagCustomData: String?
  var customModuleOptions: Map<String, String>
  var module: ModuleEntityBuilder
}

internal object ModuleCustomImlDataEntityType : EntityType<ModuleCustomImlDataEntity, ModuleCustomImlDataEntityBuilder>() {
  override val entityClass: Class<ModuleCustomImlDataEntity> get() = ModuleCustomImlDataEntity::class.java
  operator fun invoke(
    customModuleOptions: Map<String, String>,
    entitySource: EntitySource,
    init: (ModuleCustomImlDataEntityBuilder.() -> Unit)? = null,
  ): ModuleCustomImlDataEntityBuilder {
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
  modification: ModuleCustomImlDataEntityBuilder.() -> Unit,
): ModuleCustomImlDataEntity = modifyEntity(ModuleCustomImlDataEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createModuleCustomImlDataEntity")
fun ModuleCustomImlDataEntity(
  customModuleOptions: Map<String, String>,
  entitySource: EntitySource,
  init: (ModuleCustomImlDataEntityBuilder.() -> Unit)? = null,
): ModuleCustomImlDataEntityBuilder = ModuleCustomImlDataEntityType(customModuleOptions, entitySource, init)
