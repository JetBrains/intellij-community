// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableModuleTestEntity : ModifiableWorkspaceEntity<ModuleTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var contentRoots: List<ModifiableContentRootTestEntity>
  var facets: List<ModifiableFacetTestEntity>
}

internal object ModuleTestEntityType : EntityType<ModuleTestEntity, ModifiableModuleTestEntity>() {
  override val entityClass: Class<ModuleTestEntity> get() = ModuleTestEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableModuleTestEntity.() -> Unit)? = null,
  ): ModifiableModuleTestEntity {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyModuleTestEntity(
  entity: ModuleTestEntity,
  modification: ModifiableModuleTestEntity.() -> Unit,
): ModuleTestEntity = modifyEntity(ModifiableModuleTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createModuleTestEntity")
fun ModuleTestEntity(
  name: String,
  entitySource: EntitySource,
  init: (ModifiableModuleTestEntity.() -> Unit)? = null,
): ModifiableModuleTestEntity = ModuleTestEntityType(name, entitySource, init)
