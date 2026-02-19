// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Abstract

@Abstract
interface ModuleSettingsFacetBridgeEntity : WorkspaceEntityWithSymbolicId {
  val moduleId: ModuleId
  val name: @NlsSafe String

  //region generated code
  @Deprecated(message = "Use ModuleSettingsFacetBridgeEntityBuilder instead")
  interface Builder<T : ModuleSettingsFacetBridgeEntity> : ModuleSettingsFacetBridgeEntityBuilder<T>
  companion object : EntityType<ModuleSettingsFacetBridgeEntity, Builder<ModuleSettingsFacetBridgeEntity>>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      moduleId: ModuleId,
      name: String,
      entitySource: EntitySource,
      init: (Builder<ModuleSettingsFacetBridgeEntity>.() -> Unit)? = null,
    ): Builder<ModuleSettingsFacetBridgeEntity> =
      ModuleSettingsFacetBridgeEntityType.compatibilityInvoke(moduleId, name, entitySource, init)
  }
  //endregion
}