// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleSourcePackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModuleSourcePackagingElementEntityBuilder : WorkspaceEntityBuilder<ModuleSourcePackagingElementEntity>,
                                                      PackagingElementEntity.Builder<ModuleSourcePackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleSourcePackagingElementEntityType :
  EntityType<ModuleSourcePackagingElementEntity, ModuleSourcePackagingElementEntityBuilder>() {
  override val entityClass: Class<ModuleSourcePackagingElementEntity> get() = ModuleSourcePackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModuleSourcePackagingElementEntityBuilder.() -> Unit)? = null,
  ): ModuleSourcePackagingElementEntityBuilder {
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
  modification: ModuleSourcePackagingElementEntityBuilder.() -> Unit,
): ModuleSourcePackagingElementEntity = modifyEntity(ModuleSourcePackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleSourcePackagingElementEntity")
fun ModuleSourcePackagingElementEntity(
  entitySource: EntitySource,
  init: (ModuleSourcePackagingElementEntityBuilder.() -> Unit)? = null,
): ModuleSourcePackagingElementEntityBuilder = ModuleSourcePackagingElementEntityType(entitySource, init)
