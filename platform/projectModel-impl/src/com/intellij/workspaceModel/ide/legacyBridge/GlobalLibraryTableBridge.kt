// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.libraries.LibraryTable
import org.jetbrains.annotations.ApiStatus

/**
 * Utility interface to provide bridge behaviour from entities to [com.intellij.openapi.roots.libraries.Library]
 */
@ApiStatus.Internal
interface GlobalLibraryTableBridge : GlobalEntityBridgeAndEventHandler, LibraryTable {
  companion object {
    fun getInstance(): GlobalLibraryTableBridge = ApplicationManager.getApplication().service()
  }
}