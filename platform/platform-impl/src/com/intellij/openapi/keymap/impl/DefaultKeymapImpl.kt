/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import java.awt.event.MouseEvent

open class DefaultKeymapImpl(dataHolder: SchemeDataHolder<KeymapImpl>, private val defaultKeymapManager: DefaultKeymap) : KeymapImpl(dataHolder) {
  override final var canModify: Boolean
    get() = false
    set(value) {
      // ignore
    }

  override fun getSchemeState(): SchemeState = SchemeState.NON_PERSISTENT

  override fun getPresentableName(): String = DefaultKeymap.instance.getKeymapPresentableName(this)

  override fun readExternal(keymapElement: Element) {
    super.readExternal(keymapElement)

    if (KeymapManager.DEFAULT_IDEA_KEYMAP == name && !SystemInfo.isXWindow) {
      addShortcut(IdeActions.ACTION_GOTO_DECLARATION, MouseShortcut(MouseEvent.BUTTON2, 0, 1))
    }
  }

  // default keymap can have parent only in the defaultKeymapManager
  // also, it allows us to avoid dependency on KeymapManager (maybe not initialized yet)
  override fun findParentScheme(parentSchemeName: String): Keymap? = defaultKeymapManager.findScheme(parentSchemeName)
}
