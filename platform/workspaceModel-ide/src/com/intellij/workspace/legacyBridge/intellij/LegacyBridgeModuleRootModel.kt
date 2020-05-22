// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.workspace.api.TypedEntityStorage

internal interface LegacyBridgeModuleRootModel: ModuleRootModel {
  val storage: TypedEntityStorage
  val legacyBridgeModule: LegacyBridgeModule
  val accessor: RootConfigurationAccessor
}