// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@Abstract
interface ModuleSettingsFacetBridgeEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String
  @Parent
  val module: ModuleEntity

  @Deprecated(message = "Use ModuleSettingsFacetBridgeEntityBuilder instead")
  interface Builder<T : ModuleSettingsFacetBridgeEntity> : ModuleSettingsFacetBridgeEntityBuilder<T>
}