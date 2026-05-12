// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.QuickList
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

internal class ActionsTreeRendererHelper(private val userObject: Any, keymap: Keymap?) {

  @JvmField
  val shortcuts: Array<Shortcut>

  @JvmField
  val abbreviations: Set<String>

  init {
    val actionId: String? = when (userObject) {
      is String -> userObject
      is QuickList -> userObject.actionId
      is Group -> userObject.id
      else -> null
    }

    if (actionId == null || keymap == null) {
      shortcuts = emptyArray()
      abbreviations = emptySet()
    }
    else {
      shortcuts = keymap.getShortcuts(actionId)
      abbreviations = AbbreviationManager.getInstance().getAbbreviations(actionId)
    }
  }

  fun getAccessibleString(): @Nls String? {
    @NlsSafe
    val result = shortcuts.joinToString(", ") {
      KeyMapBundle.message("accessible.name.shortcut", getShortcutText(it))
    }
    return result.ifEmpty { null }
  }
}
