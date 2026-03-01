// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalSystemModuleOptionsEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ExternalSystemModuleOptionsEntityBuilder : WorkspaceEntityBuilder<ExternalSystemModuleOptionsEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var externalSystem: String?
  var externalSystemModuleVersion: String?
  var linkedProjectPath: String?
  var linkedProjectId: String?
  var rootProjectPath: String?
  var externalSystemModuleGroup: String?
  var externalSystemModuleType: String?
}

internal object ExternalSystemModuleOptionsEntityType :
  EntityType<ExternalSystemModuleOptionsEntity, ExternalSystemModuleOptionsEntityBuilder>() {
  override val entityClass: Class<ExternalSystemModuleOptionsEntity> get() = ExternalSystemModuleOptionsEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ExternalSystemModuleOptionsEntityBuilder.() -> Unit)? = null,
  ): ExternalSystemModuleOptionsEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ExternalSystemModuleOptionsEntity.Builder.() -> Unit)? = null,
  ): ExternalSystemModuleOptionsEntity.Builder {
    val builder = builder() as ExternalSystemModuleOptionsEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyExternalSystemModuleOptionsEntity(
  entity: ExternalSystemModuleOptionsEntity,
  modification: ExternalSystemModuleOptionsEntityBuilder.() -> Unit,
): ExternalSystemModuleOptionsEntity = modifyEntity(ExternalSystemModuleOptionsEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createExternalSystemModuleOptionsEntity")
fun ExternalSystemModuleOptionsEntity(
  entitySource: EntitySource,
  init: (ExternalSystemModuleOptionsEntityBuilder.() -> Unit)? = null,
): ExternalSystemModuleOptionsEntityBuilder = ExternalSystemModuleOptionsEntityType(entitySource, init)
