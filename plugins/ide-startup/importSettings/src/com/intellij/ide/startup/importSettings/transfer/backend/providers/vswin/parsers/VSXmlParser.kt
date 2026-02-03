// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers

import com.intellij.ide.startup.importSettings.db.KnownKeymaps
import com.intellij.ide.startup.importSettings.models.PatchedKeymap
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vswin.parsers.data.FontsAndColorsParsedData
import com.intellij.ide.startup.importSettings.providers.vswin.parsers.data.VSParsedData
import com.intellij.ide.startup.importSettings.providers.vswin.parsers.data.VSParsedDataCreator
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.Version2
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers.data.KeyBindingsParsedData
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities.VSHive
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class VSXmlParserException(message: String) : Exception(message)

class VSXmlParser(settingsFile: Path, private val hive: VSHive? = null) {
  companion object {
    const val APPLICATION_IDENTITY: String = "ApplicationIdentity"
    const val TOOLS_OPTIONS: String = "ToolsOptions"
    const val CATEGORY: String = "Category"
    const val ENVIRONMENT_GROUP: String = "Environment_Group"
    const val NAME_ATTRIBUTE: String = "name"
    const val VERSION_ATTRIBUTE: String = "version"

    private val logger = logger<VSXmlParser>()
  }

  val allSettings: Settings = Settings(
    keymap = KnownKeymaps.VisualStudio2022
  )
  val ver: Version2

  init {
    require(settingsFile.exists()) { "Settings file was not found" }
    if (hive != null) {
      logger.info("Parsing $hive")
    }
    val rootElement = JDOMUtil.load(settingsFile)

    logger.info("Parsing file ${settingsFile.absolutePathString()}")
    val verStr = rootElement.getChild(APPLICATION_IDENTITY)?.getAttribute(VERSION_ATTRIBUTE)?.value
                 ?: throw VSXmlParserException("Can't find version")
    ver = Version2.parse(verStr)
    categoryDigger(ver, rootElement)
  }

  fun toSettings(): Settings {
    return allSettings
  }

  private fun categoryDigger(version: Version2, rtElement: Element) {
    for (el in rtElement.children) {
      if (el.name == APPLICATION_IDENTITY) continue
      if (el.name == TOOLS_OPTIONS || (el.name == CATEGORY && el.getAttribute(NAME_ATTRIBUTE)?.value == ENVIRONMENT_GROUP)) {
        categoryDigger(version, el)
        continue
      }

      val disp = parserDispatcher(version, el, hive)?.let { it() }
      if (disp != null) {
        val name = el?.getAttribute(NAME_ATTRIBUTE)?.value
        if (name == null) {
          logger.info("This should not happen. For some reason there is no name attribute")
          continue
        }

        when (disp) {
          is FontsAndColorsParsedData -> allSettings.laf = disp.theme.toRiderTheme()
          is KeyBindingsParsedData -> {
            val format = disp.convertToSettingsFormat()
            val oldKeymap = allSettings.keymap
            if (format.isNotEmpty() && oldKeymap != null) {
              allSettings.keymap =
                PatchedKeymap(oldKeymap.transferableId, oldKeymap, disp.convertToSettingsFormat(), emptyList())
            }
          }
        }
      }
    }
  }

  private fun parserDispatcher(version: Version2, el: Element, hive: VSHive?): (() -> VSParsedData?)? {
    //.debug("Processing $value")
    return when (el.getAttribute(NAME_ATTRIBUTE)?.value) {
      FontsAndColorsParsedData.key -> {
        { VSParsedDataCreator.fontsAndColors(version, el) }
      }
      KeyBindingsParsedData.KEY -> {
        { VSParsedDataCreator.keyBindings(version, el, hive) }
      }
      //DebuggerParsedData.key -> { { VSParsedDataCreator.debugger(version, el) } }
      //ToolWindowsParsedData.key -> { { VSParsedDataCreator.toolWindows(version, el) } }
      //else -> { logger.debug("Unknown").let { null } }
      else -> null
    }
  }
}