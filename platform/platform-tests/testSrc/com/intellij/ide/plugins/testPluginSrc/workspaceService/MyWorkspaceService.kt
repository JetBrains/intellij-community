// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.workspaceService

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.platform.testFramework.plugins.PluginTestHandle

internal class MyWorkspaceService : PluginTestHandle<() -> Unit, Unit>, Disposable {
  private var onDispose: (() -> Unit)? = null

  override fun test(arg: () -> Unit) {
    onDispose = arg
  }

  override fun dispose() {
    onDispose?.invoke()
  }
}

@Service(Service.Level.WORKSPACE)
internal class MyLightWorkspaceService : PluginTestHandle<() -> Unit, Unit>, Disposable {
  private var onDispose: (() -> Unit)? = null

  override fun test(arg: () -> Unit) {
    onDispose = arg
  }

  override fun dispose() {
    onDispose?.invoke()
  }
}
