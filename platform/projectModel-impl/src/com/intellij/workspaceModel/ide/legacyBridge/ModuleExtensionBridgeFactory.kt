// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.ModuleExtension
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage

/**
 * Register implementation of this interface as `com.intellij.workspaceModel.moduleExtensionBridgeFactory` extension to provide implementations
 * of [ModuleExtension] based on data from workspace model.
 */
interface ModuleExtensionBridgeFactory<T> where T : ModuleExtension, T : ModuleExtensionBridge {
  fun createExtension(module: ModuleBridge, entityStorage: VersionedEntityStorage, diff: MutableEntityStorage?): T
}