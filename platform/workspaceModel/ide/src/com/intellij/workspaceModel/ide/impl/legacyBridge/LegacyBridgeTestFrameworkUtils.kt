// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProvider
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProviderImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import org.jetbrains.annotations.ApiStatus

object LegacyBridgeTestFrameworkUtils {
  @ApiStatus.Internal
  fun dropCachesOnTeardown(project: Project) {
    if (!LegacyBridgeProjectLifecycleListener.enabled(project)) {
      return
    }

    WriteAction.runAndWait<RuntimeException> {
      for (module in ModuleManager.getInstance(project).modules) {
        (FilePointerProvider.getInstance(module) as FilePointerProviderImpl).clearCaches()
        (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge).dropCaches()
      }
      (FilePointerProvider.getInstance(project) as FilePointerProviderImpl).clearCaches()
    }
  }
}