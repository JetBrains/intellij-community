// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleGroupPathEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModuleGroupPathEntityBuilder : WorkspaceEntityBuilder<ModuleGroupPathEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var path: MutableList<String>
}

internal object ModuleGroupPathEntityType : EntityType<ModuleGroupPathEntity, ModuleGroupPathEntityBuilder>() {
  override val entityClass: Class<ModuleGroupPathEntity> get() = ModuleGroupPathEntity::class.java
  operator fun invoke(
    path: List<String>,
    entitySource: EntitySource,
    init: (ModuleGroupPathEntityBuilder.() -> Unit)? = null,
  ): ModuleGroupPathEntityBuilder {
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
  modification: ModuleGroupPathEntityBuilder.() -> Unit,
): ModuleGroupPathEntity = modifyEntity(ModuleGroupPathEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createModuleGroupPathEntity")
fun ModuleGroupPathEntity(
  path: List<String>,
  entitySource: EntitySource,
  init: (ModuleGroupPathEntityBuilder.() -> Unit)? = null,
): ModuleGroupPathEntityBuilder = ModuleGroupPathEntityType(path, entitySource, init)
