// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.parsers

import com.intellij.ide.startup.importSettings.models.KeyBinding
import com.intellij.ide.startup.importSettings.models.PatchedKeymap
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vsmac.mappings.KeyBindingsMappings
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.SmartList
import org.jdom.Element
import java.nio.file.Path
import javax.swing.KeyStroke

private val logger = logger<KeyBindingsParser>()

class KeyBindingsParser(private val settings: Settings) {
  companion object {
    private const val CURRENT = "current"
    private const val COMMAND = "command"
    private const val SHORTCUT = "shortcut"
  }

  private val customShortcuts = mutableListOf<KeyBinding>()

  fun process(file: Path): Unit = try {
    logger.info("Processing a file: $file")

    val root = JDOMUtil.load(file)

    processKeyBindings(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processKeyBindings(root: Element) {
    val currentScheme = root.children.firstOrNull { scheme ->
      val schemeAttributes = scheme?.attributes ?: return@firstOrNull false
      return@firstOrNull schemeAttributes.size == 1 && schemeAttributes[0]?.value == CURRENT
    } ?: return

    processCurrentScheme(currentScheme)

    val km = settings.keymap
    if (customShortcuts.isNotEmpty() && km != null) {
      settings.keymap = PatchedKeymap(km.transferableId, km, customShortcuts, emptyList())
    }
  }

  private fun processCurrentScheme(scheme: Element) {
    scheme.children.forEach { binding ->
      try {
        val attributes = binding?.attributes ?: return@forEach

        if (!(attributes.size == 2 && attributes[0]?.name?.equals(COMMAND) == true && attributes[1]?.name?.equals(SHORTCUT) == true)) {
          return@forEach
        }

        val commandId = attributes[0]?.value ?: return@forEach
        val shortcuts = attributes[1]?.value ?: return@forEach

        addCustomShortcut(commandId, shortcuts)
      }
      catch (t: Throwable) {
        logger.warn(t)
      }
    }
  }

  private fun addCustomShortcut(foreignCommandId: String, foreignShortcuts: String) {
    val commandId = KeyBindingsMappings.commandIdMap(foreignCommandId) ?: return
    val shortcuts = parseShortcuts(foreignShortcuts) ?: return

    if (KeyBindingsMappings.defaultVSMacKeymap[commandId] != shortcuts) {
      customShortcuts.add(KeyBinding(commandId, shortcuts))
    }
  }

  private fun parseShortcuts(str: String): List<KeyboardShortcut>? {
    val shortcuts = SmartList<KeyboardShortcut>()

    str.split(' ').forEach { shortcut ->
      val strokes = shortcut.split('|')

      val firstKeyStroke = getKeyStroke(strokes.getOrNull(0)) ?: return null
      val secondKeyStroke = getKeyStroke(strokes.getOrNull(1))

      shortcuts += KeyboardShortcut(firstKeyStroke, secondKeyStroke)
    }

    return shortcuts.ifEmpty { null }
  }

  private fun getKeyStroke(s: String?): KeyStroke? {
    if (s.isNullOrEmpty()) {
      return null
    }

    val sb = StringBuilder()
    s.replace("++", "+Plus").split('+').forEach {
      val normalizedShortcut = KeyBindingsMappings.shortcutMap(it)
      sb.append("$normalizedShortcut ")
    }

    return KeyStroke.getKeyStroke(sb.dropLast(1).toString())
  }
}