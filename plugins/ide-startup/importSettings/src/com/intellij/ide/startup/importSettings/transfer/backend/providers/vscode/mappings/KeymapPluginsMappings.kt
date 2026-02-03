// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode.mappings

import com.intellij.ide.startup.importSettings.models.Keymap
import com.intellij.openapi.util.SystemInfoRt

@Suppress("FunctionName")
object KeymapPluginsMappings {
  fun map(id: String): Keymap? {
    return when (id) {
      //"k--kato.intellij-idea-keybindings" -> SystemDep(KnownKeymaps.IntelliJMacOS, KnownKeymaps.IntelliJ)
      else -> null
    }
  }

  private fun SystemDep(mac: Keymap, other: Keymap) = if (SystemInfoRt.isMac) mac else other
}