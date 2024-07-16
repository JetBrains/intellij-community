// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GlobalSdkTableBridge: GlobalEntityBridgeAndEventHandler {
  companion object {
    fun getInstance(): GlobalSdkTableBridge = ApplicationManager.getApplication().service()
  }
}