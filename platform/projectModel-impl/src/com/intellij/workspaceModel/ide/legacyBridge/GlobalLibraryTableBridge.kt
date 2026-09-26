// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.platform.eel.EelMachine
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility interface to provide bridge behaviour from entities to [com.intellij.openapi.roots.libraries.Library]
 */
@ApiStatus.Internal
interface GlobalLibraryTableBridge : GlobalEntityBridgeAndEventHandler, LibraryTable {
  companion object {
    fun getInstance(eelMachine: EelMachine): GlobalLibraryTableBridge = ApplicationManager.getApplication().service<GlobalLibraryTableBridgeRegistry>().getTableBridge(eelMachine)
  }
}


@Service(Service.Level.APP)
private class GlobalLibraryTableBridgeRegistry {
  private val registry: MutableMap<EelMachine, GlobalLibraryTableBridge> = ConcurrentHashMap()

  fun getTableBridge(eelMachine: EelMachine): GlobalLibraryTableBridge {
    return registry.computeIfAbsent(eelMachine) { GlobalLibraryTableBridgeImpl(it) }
  }
}