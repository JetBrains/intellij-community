// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleOutputPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModuleOutputPackagingElementEntityBuilder : WorkspaceEntityBuilder<ModuleOutputPackagingElementEntity>,
                                                      PackagingElementEntity.Builder<ModuleOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleOutputPackagingElementEntityType :
  EntityType<ModuleOutputPackagingElementEntity, ModuleOutputPackagingElementEntityBuilder>() {
  override val entityClass: Class<ModuleOutputPackagingElementEntity> get() = ModuleOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModuleOutputPackagingElementEntityBuilder.() -> Unit)? = null,
  ): ModuleOutputPackagingElementEntityBuilder {
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
  modification: ModuleOutputPackagingElementEntityBuilder.() -> Unit,
): ModuleOutputPackagingElementEntity = modifyEntity(ModuleOutputPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleOutputPackagingElementEntity")
fun ModuleOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModuleOutputPackagingElementEntityBuilder.() -> Unit)? = null,
): ModuleOutputPackagingElementEntityBuilder = ModuleOutputPackagingElementEntityType(entitySource, init)
