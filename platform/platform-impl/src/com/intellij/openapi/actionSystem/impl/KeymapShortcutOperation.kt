// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.KeymapUtil.getKeyStroke
import com.intellij.util.xml.dom.XmlElement
import javax.swing.KeyStroke

private const val KEYMAP_ATTR_NAME = "keymap"

internal sealed interface KeymapShortcutOperation

internal data class AddShortcutOperation(@JvmField val actionId: String, @JvmField val shortcut: Shortcut) : KeymapShortcutOperation

internal data class RemoveAllShortcutsOperation(@JvmField val actionId: String) : KeymapShortcutOperation

internal data class RemoveShortcutOperation(@JvmField val actionId: String, @JvmField val shortcut: Shortcut) : KeymapShortcutOperation

internal fun processMouseShortcutNode(element: XmlElement,
                                      actionId: String,
                                      module: IdeaPluginDescriptor,
                                      keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val keystrokeString = element.attributes.get("keystroke")
  if (keystrokeString.isNullOrBlank()) {
    reportActionError(module, "\"keystroke\" attribute must be specified for action with id=$actionId")
    return
  }

  val shortcut = try {
    KeymapUtil.parseMouseShortcut(keystrokeString)
  }
  catch (_: Exception) {
    reportActionError(module, "\"keystroke\" attribute has invalid value for action with id=$actionId")
    return
  }

  val keymapName = element.attributes.get(KEYMAP_ATTR_NAME)
  if (keymapName.isNullOrEmpty()) {
    reportActionError(module, "attribute \"keymap\" should be defined")
    return
  }

  processRemoveAndReplace(element = element,
                          actionId = actionId,
                          keymap = keymapName,
                          shortcut = shortcut,
                          keymapToOperations = keymapToOperations)
}

internal fun processKeyboardShortcutNode(element: XmlElement,
                                         actionId: String,
                                         module: PluginDescriptor,
                                         keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val firstStrokeString = element.attributes.get("first-keystroke")
  if (firstStrokeString == null) {
    reportActionError(module, "\"first-keystroke\" attribute must be specified for action with id=$actionId")
    return
  }

  val firstKeyStroke = getKeyStroke(firstStrokeString)
  if (firstKeyStroke == null) {
    reportActionError(module = module, message = "\"first-keystroke\" attribute has invalid value for action with id=$actionId")
    return
  }

  var secondKeyStroke: KeyStroke? = null
  val secondStrokeString = element.attributes.get("second-keystroke")
  if (secondStrokeString != null) {
    secondKeyStroke = getKeyStroke(secondStrokeString)
    if (secondKeyStroke == null) {
      reportActionError(module = module, message = "\"second-keystroke\" attribute has invalid value for action with id=$actionId")
      return
    }
  }

  val keymapName = element.attributes.get(KEYMAP_ATTR_NAME)
  if (keymapName.isNullOrBlank()) {
    reportActionError(module = module, message = "attribute \"keymap\" should be defined")
    return
  }

  processRemoveAndReplace(element = element,
                          actionId = actionId,
                          keymap = keymapName,
                          shortcut = KeyboardShortcut(firstKeyStroke, secondKeyStroke),
                          keymapToOperations = keymapToOperations)
}

private fun processRemoveAndReplace(element: XmlElement,
                                    actionId: String,
                                    keymap: String,
                                    shortcut: Shortcut,
                                    keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val operations = keymapToOperations.computeIfAbsent(keymap) { ArrayList() }
  val remove = element.attributes.get("remove").toBoolean()
  if (remove) {
    operations.add(RemoveShortcutOperation(actionId, shortcut))
  }

  val replace = element.attributes.get("replace-all").toBoolean()
  if (replace) {
    operations.add(RemoveAllShortcutsOperation(actionId))
  }
  if (!remove) {
    operations.add(AddShortcutOperation(actionId, shortcut))
  }
}

private fun reportActionError(module: PluginDescriptor, message: String, cause: Throwable? = null) {
  logger<ActionManager>().error(PluginException("$message (module=$module)", cause, module.pluginId))
}