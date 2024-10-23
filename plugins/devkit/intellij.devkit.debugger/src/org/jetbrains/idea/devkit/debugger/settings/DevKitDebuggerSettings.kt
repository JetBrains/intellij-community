// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger.settings

import com.intellij.openapi.components.*

@State(name = "DevKitDebuggerApplicationSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.APP)
internal class DevKitDebuggerSettings {
  var showIdeState = true

  companion object {
    fun getInstance(): DevKitDebuggerSettings = service()
  }
}
