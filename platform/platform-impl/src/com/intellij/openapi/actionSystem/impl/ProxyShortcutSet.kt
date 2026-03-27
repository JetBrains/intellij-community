// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapManager
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ProxyShortcutSet internal constructor(val actionId: String) : ShortcutSet {
  override fun getShortcuts(): Array<Shortcut> {
    return KeymapManager.getInstance()?.getActiveKeymap()?.getShortcuts(actionId) ?: Shortcut.EMPTY_ARRAY
  }
}