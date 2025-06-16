// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Abstract

@Abstract
interface ModuleSettingsFacetBridgeEntity : WorkspaceEntityWithSymbolicId {
  val moduleId: ModuleId
  val name: @NlsSafe String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : ModuleSettingsFacetBridgeEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var moduleId: ModuleId
    var name: String
  }

  companion object : EntityType<ModuleSettingsFacetBridgeEntity, Builder<ModuleSettingsFacetBridgeEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      moduleId: ModuleId,
      name: String,
      entitySource: EntitySource,
      init: (Builder<ModuleSettingsFacetBridgeEntity>.() -> Unit)? = null,
    ): Builder<ModuleSettingsFacetBridgeEntity> {
      val builder = builder()
      builder.moduleId = moduleId
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}