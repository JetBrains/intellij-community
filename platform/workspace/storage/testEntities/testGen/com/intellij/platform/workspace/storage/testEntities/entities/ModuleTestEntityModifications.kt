// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleTestEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ModuleTestEntityBuilder : WorkspaceEntityBuilder<ModuleTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var contentRoots: List<ContentRootTestEntityBuilder>
  var facets: List<FacetTestEntityBuilder>
}

internal object ModuleTestEntityType : EntityType<ModuleTestEntity, ModuleTestEntityBuilder>() {
  override val entityClass: Class<ModuleTestEntity> get() = ModuleTestEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModuleTestEntityBuilder.() -> Unit)? = null,
  ): ModuleTestEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleTestEntity(
  entity: ModuleTestEntity,
  modification: ModuleTestEntityBuilder.() -> Unit,
): ModuleTestEntity = modifyEntity(ModuleTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleTestEntity")
fun ModuleTestEntity(
  name: String,
  entitySource: EntitySource,
  init: (ModuleTestEntityBuilder.() -> Unit)? = null,
): ModuleTestEntityBuilder = ModuleTestEntityType(name, entitySource, init)
