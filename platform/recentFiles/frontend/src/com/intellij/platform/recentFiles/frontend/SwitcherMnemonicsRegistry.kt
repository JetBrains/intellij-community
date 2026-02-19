// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.util.BitUtil.isSet
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class SwitcherMnemonicsRegistry(
  private val launchParameters: SwitcherLaunchEventParameters
) {

  val forbiddenMnemonic: String?
    get() = launchParameters.keyCode?.let { getMnemonic(it) }

  fun getForbiddenMnemonic(
    keyStroke: KeyStroke,
  ): String? {
    return when {
      isSet(keyStroke.modifiers, InputEvent.ALT_DOWN_MASK) != launchParameters.wasAltDown -> null
      isSet(keyStroke.modifiers, InputEvent.ALT_GRAPH_DOWN_MASK) != launchParameters.wasAltGraphDown -> null
      isSet(keyStroke.modifiers, InputEvent.CTRL_DOWN_MASK) != launchParameters.wasControlDown -> null
      isSet(keyStroke.modifiers, InputEvent.META_DOWN_MASK) != launchParameters.wasMetaDown -> null
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