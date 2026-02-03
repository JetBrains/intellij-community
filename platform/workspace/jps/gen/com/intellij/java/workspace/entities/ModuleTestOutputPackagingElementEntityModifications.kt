// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleTestOutputPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModuleTestOutputPackagingElementEntityBuilder : WorkspaceEntityBuilder<ModuleTestOutputPackagingElementEntity>,
                                                          PackagingElementEntity.Builder<ModuleTestOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var module: ModuleId?
}

internal object ModuleTestOutputPackagingElementEntityType :
  EntityType<ModuleTestOutputPackagingElementEntity, ModuleTestOutputPackagingElementEntityBuilder>() {
  override val entityClass: Class<ModuleTestOutputPackagingElementEntity> get() = ModuleTestOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModuleTestOutputPackagingElementEntityBuilder.() -> Unit)? = null,
  ): ModuleTestOutputPackagingElementEntityBuilder {
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
  modification: ModuleTestOutputPackagingElementEntityBuilder.() -> Unit,
): ModuleTestOutputPackagingElementEntity = modifyEntity(ModuleTestOutputPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleTestOutputPackagingElementEntity")
fun ModuleTestOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModuleTestOutputPackagingElementEntityBuilder.() -> Unit)? = null,
): ModuleTestOutputPackagingElementEntityBuilder = ModuleTestOutputPackagingElementEntityType(entitySource, init)
