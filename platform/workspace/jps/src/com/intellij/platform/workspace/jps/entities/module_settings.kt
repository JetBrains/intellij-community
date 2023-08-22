// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Abstract

@Abstract
interface ModuleSettingsBase : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String
  val moduleId: ModuleId

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : ModuleSettingsBase> : ModuleSettingsBase, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var name: String
    override var moduleId: ModuleId
  }

  companion object : EntityType<ModuleSettingsBase, Builder<ModuleSettingsBase>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        moduleId: ModuleId,
                        entitySource: EntitySource,
                        init: (Builder<ModuleSettingsBase>.() -> Unit)? = null): ModuleSettingsBase {
      val builder = builder()
      builder.name = name
      builder.moduleId = moduleId
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}