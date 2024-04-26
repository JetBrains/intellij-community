// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.parsers.data

import com.intellij.ide.startup.importSettings.providers.vswin.mappings.FontsAndColorsMappings
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.VSHive
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.Version2
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers.data.KeyBindingsParsedData
import com.intellij.openapi.diagnostic.logger
import org.jdom.Element

object VSParsedDataCreator {
  private val logger = logger<VSParsedDataCreator>()

  fun fontsAndColors(version: Version2, el: Element): VSParsedData? {
    when (version.major) {
      10 -> return FontsAndColorsParsedData(FontsAndColorsMappings.VsTheme.Blue.themeUuid)
      11, 12, 14, 15, 16, 17 -> {}
      else -> return null
    }
    val foc = el.getChild("FontsAndColors")
              ?: return null // 0_o
    val themeUuid = foc.getChild("Theme")?.getAttribute("Id")?.value?.let { it.subSequence(1, it.length - 1) }?.toString()
                    ?: return null // Не нашли uuid темы
    return try {
      FontsAndColorsParsedData(themeUuid)
    }
    catch (t: Throwable) {
      logger.info(t)
      null
    }
  }

  fun keyBindings(version: Version2, el: Element, hive: VSHive?): VSParsedData? {
    when (version.major) {
      10, 11, 12, 14, 15, 16, 17 -> {}
      else -> return null
    }
    val kbSc = el.getChild("KeyboardShortcuts")
               ?: return null
    val scheme = kbSc.getChild("ShortcutsScheme")?.text
                 ?: return null
    val userSc = kbSc.getChild("UserShortcuts")
                 ?: return null

    return try {
      KeyBindingsParsedData(
        scheme,
        userSc,
        hive
      )
    }
    catch (t: Throwable) {
      logger.info(t)
      null
    }
  }
}