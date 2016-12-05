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

import com.intellij.ide.WelcomeWizardUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.NonLazySchemeProcessor
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*

internal const val KEYMAPS_DIR_PATH = "keymaps"

private const val ACTIVE_KEYMAP = "active_keymap"
private const val NAME_ATTRIBUTE = "name"

var ourKeymapManagerInitialized: Boolean = false

@State(name = "KeymapManager", storages = arrayOf(Storage(value = "keymap.xml", roamingType = RoamingType.PER_OS)), additionalExportFile = KEYMAPS_DIR_PATH)
class KeymapManagerImpl internal constructor(defaultKeymap: DefaultKeymap, factory: SchemeManagerFactory) : KeymapManagerEx(), PersistentStateComponent<Element> {
  private val myListeners = ContainerUtil.createLockFreeCopyOnWriteList<KeymapManagerListener>()
  private val myBoundShortcuts = HashMap<String, String>()
  private val mySchemeManager: SchemeManager<Keymap>

  init {
    mySchemeManager = factory.create(KEYMAPS_DIR_PATH, object : NonLazySchemeProcessor<Keymap, KeymapImpl>() {
      @Throws(InvalidDataException::class)
      override fun readScheme(element: Element, duringLoad: Boolean): KeymapImpl {
        val keymap = KeymapImpl()
        keymap.readExternal(element, allIncludingDefaultsKeymaps)
        return keymap
      }

      override fun writeScheme(scheme: KeymapImpl): Element {
        return scheme.writeScheme()
      }

      override fun getState(scheme: Keymap) = if (scheme.canModify()) SchemeState.POSSIBLY_CHANGED else SchemeState.NON_PERSISTENT

      override fun onCurrentSchemeSwitched(oldScheme: Keymap?, newScheme: Keymap?) {
        for (listener in myListeners) {
          listener.activeKeymapChanged(newScheme)
        }
      }
    })

    val systemDefaultKeymap = if (WelcomeWizardUtil.getWizardMacKeymap() != null)
      WelcomeWizardUtil.getWizardMacKeymap()
    else
      defaultKeymap.defaultKeymapName
    for (keymap in defaultKeymap.keymaps) {
      mySchemeManager.addScheme(keymap)
      if (keymap.name == systemDefaultKeymap) {
        activeKeymap = keymap
      }
    }
    mySchemeManager.loadSchemes()

    ourKeymapManagerInitialized = true
  }

  override fun getAllKeymaps() = getKeymaps(Conditions.alwaysTrue<Keymap>()).toTypedArray()

  fun getKeymaps(additionalFilter: Condition<Keymap>) = mySchemeManager.allSchemes.filter { !it.presentableName.startsWith("$") && additionalFilter.value(it)  }

  val allIncludingDefaultsKeymaps: Array<Keymap>
    get() = mySchemeManager.allSchemes.toTypedArray()

  override fun getKeymap(name: String) = mySchemeManager.findSchemeByName(name)

  override fun getActiveKeymap() = mySchemeManager.currentScheme

  override fun setActiveKeymap(keymap: Keymap?) = mySchemeManager.setCurrent(keymap)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    myBoundShortcuts.put(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    myBoundShortcuts.remove(targetActionId)
  }

  override fun getBoundActions() = myBoundShortcuts.keys

  override fun getActionBinding(actionId: String): String? {
    var visited: MutableSet<String>? = null
    var id = actionId
    while (true) {
      val next = myBoundShortcuts.get(id) ?: break
      if (visited == null) {
        visited = THashSet()
      }

      id = next
      if (!visited.add(id)) {
        break
      }
    }
    return if (id == actionId) null else id
  }

  override fun getSchemeManager() = mySchemeManager

  fun setKeymaps(keymaps: List<Keymap>, active: Keymap?, removeCondition: Condition<Keymap>?) {
    mySchemeManager.setSchemes(keymaps, active, removeCondition)
  }

  override fun getState(): Element {
    val result = Element("state")
    if (mySchemeManager.currentScheme != null) {
      mySchemeManager.currentScheme?.let {
        val e = Element(ACTIVE_KEYMAP)
        e.setAttribute(NAME_ATTRIBUTE, it.name)
        result.addContent(e)
      }
    }
    return result
  }

  override fun loadState(state: Element) {
    val child = state.getChild(ACTIVE_KEYMAP)
    val activeKeymapName = child?.getAttributeValue(NAME_ATTRIBUTE)
    if (!activeKeymapName.isNullOrBlank()) {
      mySchemeManager.currentSchemeName = activeKeymapName
    }
  }

  override fun addKeymapManagerListener(listener: KeymapManagerListener) {
    pollQueue()
    myListeners.add(listener)
  }

  override fun addKeymapManagerListener(listener: KeymapManagerListener, parentDisposable: Disposable) {
    pollQueue()
    myListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { removeKeymapManagerListener(listener) })
  }

  private fun pollQueue() {
    myListeners.removeAll { it is WeakKeymapManagerListener && it.isDead }
  }

  override fun removeKeymapManagerListener(listener: KeymapManagerListener) {
    pollQueue()
    myListeners.remove(listener)
  }

  override fun addWeakListener(listener: KeymapManagerListener) {
    addKeymapManagerListener(WeakKeymapManagerListener(this, listener))
  }

  override fun removeWeakListener(listenerToRemove: KeymapManagerListener) {
    myListeners.removeAll { it is WeakKeymapManagerListener && it.isWrapped(listenerToRemove) }
  }
}
