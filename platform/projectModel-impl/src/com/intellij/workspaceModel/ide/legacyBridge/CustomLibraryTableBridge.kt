// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CustomLibraryTableBridge: GlobalEntityBridgeAndEventHandler, LibraryTable {
  companion object {
    fun isEnabled(): Boolean = Registry.`is`("workspace.model.custom.library.bridge", true)
  }
}