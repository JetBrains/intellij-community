// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import java.awt.event.MouseEvent

open class DefaultKeymapImpl(dataHolder: SchemeDataHolder<KeymapImpl>,
                             private val defaultKeymapManager: DefaultKeymap,
                             val plugin: PluginDescriptor) : KeymapImpl(dataHolder) {
  final override var canModify: Boolean
    get() = false
    set(_) {
      // ignore
    }

  override fun getSchemeState(): SchemeState = SchemeState.NON_PERSISTENT

  override fun getPresentableName(): String = DefaultKeymap.getInstance().getKeymapPresentableName(this)

  override fun readExternal(keymapElement: Element) {
    super.readExternal(keymapElement)

    if (KeymapManager.DEFAULT_IDEA_KEYMAP == name && (SystemInfo.isWindows || SystemInfo.isMac)) {
      addShortcut(IdeActions.ACTION_GOTO_DECLARATION, MouseShortcut(MouseEvent.BUTTON2, 0, 1))
    }
  }

  // default keymap can have parent only in the defaultKeymapManager
  // also, it allows us to avoid dependency on KeymapManager (maybe not initialized yet)
  override fun findParentScheme(parentSchemeName: String): Keymap? = defaultKeymapManager.findScheme(parentSchemeName)
}
