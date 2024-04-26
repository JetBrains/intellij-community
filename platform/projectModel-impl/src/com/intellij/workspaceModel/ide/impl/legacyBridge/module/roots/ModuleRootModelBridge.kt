// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

internal interface ModuleRootModelBridge : ModuleRootModel {
  val storage: EntityStorage
  val moduleBridge: ModuleBridge
  val accessor: RootConfigurationAccessor

  fun getOrCreateJpsRootProperties(sourceRootUrl: VirtualFileUrl, creator: () -> JpsModuleSourceRoot): JpsModuleSourceRoot
  fun removeCachedJpsRootProperties(sourceRootUrl: VirtualFileUrl)
}