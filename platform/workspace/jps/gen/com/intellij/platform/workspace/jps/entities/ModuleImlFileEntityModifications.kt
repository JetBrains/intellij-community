// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleImlFileEntityModifications") @file:Internal

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.jps.entities.impl.ModuleImlFileEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModuleImlFileEntityBuilder : WorkspaceEntityBuilder<ModuleImlFileEntity> {
  override var entitySource: EntitySource
  var url: VirtualFileUrl
  var module: ModuleEntityBuilder
}

internal object ModuleImlFileEntityType : EntityType<ModuleImlFileEntity, ModuleImlFileEntityBuilder>() {
  override val entityImplClass: Class<*> get() = ModuleImlFileEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = ModuleImlFileEntityImpl.Builder::class.java
  operator fun invoke(
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModuleImlFileEntityBuilder.() -> Unit)? = null,
  ): ModuleImlFileEntityBuilder {
    val builder = builder()
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyModuleImlFileEntity(
  entity: ModuleImlFileEntity,
  modification: ModuleImlFileEntityBuilder.() -> Unit,
): ModuleImlFileEntity = modifyEntity(ModuleImlFileEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createModuleImlFileEntity")
fun ModuleImlFileEntity(
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModuleImlFileEntityBuilder.() -> Unit)? = null,
): ModuleImlFileEntityBuilder = ModuleImlFileEntityType(url, entitySource, init)
