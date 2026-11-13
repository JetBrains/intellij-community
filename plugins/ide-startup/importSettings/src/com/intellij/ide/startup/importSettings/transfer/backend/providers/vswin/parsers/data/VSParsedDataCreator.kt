// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.parsers.data

import com.intellij.ide.startup.importSettings.providers.vswin.mappings.FontsAndColorsMappings
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.Version2
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers.data.KeyBindingsParsedData
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers.data.TextEditorGeneralParsedData
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities.VSHive
import com.intellij.openapi.diagnostic.logger
import org.jdom.Element

object VSParsedDataCreator {
  private val logger = logger<VSParsedDataCreator>()

  fun fontsAndColors(version: Version2, el: Element): VSParsedData? {
    if (version.major < 10) return null

    when (version.major) {
      10 -> return FontsAndColorsParsedData(FontsAndColorsMappings.VsTheme.Blue.themeUuid)
      else -> {}
    }

    return try {
      val foc = el.getChildOrThrow("FontsAndColors")
      val themeUuid = foc.getChild("Theme")?.getAttribute("Id")?.value?.trim('{', '}')
                      ?: throw Exception("Theme UUID not found")

      FontsAndColorsParsedData(themeUuid)
    }
    catch (t: Throwable) {
      logger.warn("Couldn't parse Visual Studio fonts and colors", t)
      null
    }
  }

  fun keyBindings(version: Version2, el: Element, hive: VSHive?): VSParsedData? {
    if (version.major < 10) return null

    return try {
      val kbSc = el.getChildOrThrow("KeyboardShortcuts")
      val scheme = kbSc.getChildOrThrow("ShortcutsScheme").text
      val userSc = kbSc.getChildOrThrow("UserShortcuts")

      KeyBindingsParsedData(
        scheme,
        userSc,
        hive
      )
    }
    catch (t: Throwable) {
      logger.warn("Couldn't parse Visual Studio key bindings", t)
      null
    }
  }

  fun textEditorGeneral(el: Element): VSParsedData? {
    // jfi no try/catching as above here because those values may not be present (i.e. default)
    val raw = el.getChildren("PropertyValue")
                .firstOrNull { it.getAttributeValue("name") == "EnableBracePairColorizationOption" }
                ?.textTrim ?: return null
    val value = raw.lowercase().toBooleanStrictOrNull() ?: return null
    return TextEditorGeneralParsedData(value)
  }

  private fun Element.getChildOrThrow(name: String): Element {
    return getChild(name) ?: throw Exception("$name element not found")
  }
}