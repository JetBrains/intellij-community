// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.Nls

internal enum class KeymapKind(val value: String, @Nls val displayName: String, @Nls val defaultLabel: String) {
  MAC(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP,
      IdeBundle.message("presentation.assistant.configurable.keymap.mac"),
      IdeBundle.message("presentation.assistant.configurable.keymap.mac.label")),
  WIN(KeymapManager.DEFAULT_IDEA_KEYMAP,
      IdeBundle.message("presentation.assistant.configurable.keymap.win"),
      IdeBundle.message("presentation.assistant.configurable.keymap.win.label"));

  val keymap = KeymapManager.getInstance().getKeymap(value)

  fun getAlternativeKind() = when (this) {
    WIN -> MAC
    MAC -> WIN
  }

  companion object {
    fun from(value: String): KeymapKind = when(value) {
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> MAC
      else -> WIN
    }
  }
}

internal fun defaultKeymapForOS() = when {
  SystemInfo.isMac -> KeymapKind.MAC
  else -> KeymapKind.WIN
}
