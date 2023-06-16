// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract

@Abstract
interface ModuleSettingsBase : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String
  val moduleId: ModuleId

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder<T : ModuleSettingsBase> : ModuleSettingsBase, WorkspaceEntity.Builder<T>, ObjBuilder<T> {
    override var entitySource: EntitySource
    override var name: String
    override var moduleId: ModuleId
  }

  companion object : Type<ModuleSettingsBase, Builder<ModuleSettingsBase>>() {
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