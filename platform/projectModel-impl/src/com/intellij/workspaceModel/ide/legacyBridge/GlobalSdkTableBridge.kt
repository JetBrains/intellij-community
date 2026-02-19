// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GlobalSdkTableBridge: GlobalEntityBridgeAndEventHandler {
  companion object {
    fun getInstance(eelMachine: EelMachine): GlobalSdkTableBridge = ApplicationManager.getApplication().service<GlobalSdkTableBridgeRegistry>().getTableBridge(eelMachine)
  }
}

@ApiStatus.Internal
interface GlobalSdkTableBridgeRegistry {
  fun getTableBridge(eelMachine: EelMachine): GlobalSdkTableBridge
}