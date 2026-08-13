// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.util.KeyedExtensionCollector

internal object ToolWindowEditorTabPersistenceProviderUtil {
  private val collector = KeyedExtensionCollector<ToolWindowEditorTabPersistenceProvider, String>(
    "com.intellij.toolWindowEditorTabPersistenceProvider",
  )

  fun getProvider(toolWindowId: String): ToolWindowEditorTabPersistenceProvider? =
    collector.forKey(toolWindowId).firstOrNull()

  fun hasProvider(toolWindowId: String): Boolean = getProvider(toolWindowId) != null
}
