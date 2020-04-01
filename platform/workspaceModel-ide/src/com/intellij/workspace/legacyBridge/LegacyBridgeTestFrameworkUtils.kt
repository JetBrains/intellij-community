// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProvider
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProviderImpl
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleRootComponent
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import org.jetbrains.annotations.ApiStatus

object LegacyBridgeTestFrameworkUtils {
  @ApiStatus.Internal
  fun dropCachesOnTeardown(project: Project) {
    if (!LegacyBridgeProjectLifecycleListener.enabled(project)) {
      return
    }

    WriteAction.runAndWait<RuntimeException> {
      for (module in ModuleManager.getInstance(project).modules) {
        (LegacyBridgeFilePointerProvider.getInstance(module) as LegacyBridgeFilePointerProviderImpl).disposeAndClearCaches()
        for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
          if (orderEntry is LibraryOrderEntry) {
            if (orderEntry.isModuleLevel) {
              (orderEntry.library as LegacyBridgeLibraryImpl).filePointerProvider.disposeAndClearCaches()
            }
          }
        }
        (ModuleRootManager.getInstance(module) as LegacyBridgeModuleRootComponent).dropCaches()
      }
      (LegacyBridgeFilePointerProvider.getInstance(project) as LegacyBridgeFilePointerProviderImpl).disposeAndClearCaches()
      for (library in LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries) {
        (library as LegacyBridgeLibraryImpl).filePointerProvider.disposeAndClearCaches()
      }
    }
  }
}