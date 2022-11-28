// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import javax.swing.KeyStroke

class GotItTextBuilder {
  fun shortcut(actionId: String): String = """<shortcut actionId="$actionId"/>"""

  /**
   * Use [KeyStroke.getKeyStroke] to create keystroke.
   *
   * For example: `KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_DOWN_MASK))`
   */
  fun shortcut(keyStroke: KeyStroke): String = """<shortcut raw="$keyStroke"/>"""

  fun icon(iconId: String): String = """<icon src="$iconId"/>"""
}