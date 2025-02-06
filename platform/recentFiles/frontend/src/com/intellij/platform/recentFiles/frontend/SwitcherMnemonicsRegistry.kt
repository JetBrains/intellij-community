// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.util.BitUtil.isSet
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class SwitcherMnemonicsRegistry(
  event: InputEvent?,
) {
  private val wasAltDown = true == event?.isAltDown
  private val wasAltGraphDown = true == event?.isAltGraphDown
  private val wasControlDown = true == event?.isControlDown
  private val wasMetaDown = true == event?.isMetaDown
  private val keyCode = (event as? KeyEvent)?.keyCode

  val forbiddenMnemonic: String?
    get() = keyCode?.let { getMnemonic(it) }

  fun getForbiddenMnemonic(
    keyStroke: KeyStroke,
  ): String? {
    return when {
      isSet(keyStroke.modifiers, InputEvent.ALT_DOWN_MASK) != wasAltDown -> null
      isSet(keyStroke.modifiers, InputEvent.ALT_GRAPH_DOWN_MASK) != wasAltGraphDown -> null
      isSet(keyStroke.modifiers, InputEvent.CTRL_DOWN_MASK) != wasControlDown -> null
      isSet(keyStroke.modifiers, InputEvent.META_DOWN_MASK) != wasMetaDown -> null
      else -> getMnemonic(keyStroke.keyCode)
    }
  }

  private fun getMnemonic(keyCode: Int): String? {
    return when (keyCode) {
      in KeyEvent.VK_0..KeyEvent.VK_9 -> keyCode.toChar().toString()
      in KeyEvent.VK_A..KeyEvent.VK_Z -> keyCode.toChar().toString()
      else -> null
    }
  }
}