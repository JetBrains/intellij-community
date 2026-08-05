// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.util.registry.Registry

internal object ToolWindowEditorTabSupportUtil {
  private val collector = KeyedExtensionCollector<ToolWindowEditorTabSupport, String>("com.intellij.toolWindowEditorTabSupport")

  const val REGISTRY_KEY: String = "toolwindow.open.tab.in.editor"

  fun isEnabled(): Boolean = Registry.`is`(REGISTRY_KEY, false)

  fun hasSupport(toolWindowId: String): Boolean = collector.forKey(toolWindowId).firstOrNull() != null
}
