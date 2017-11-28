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

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AppUIUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import gnu.trove.THashMap
import org.jdom.Element
import java.util.function.Function

internal const val KEYMAPS_DIR_PATH = "keymaps"

private const val ACTIVE_KEYMAP = "active_keymap"
private const val NAME_ATTRIBUTE = "name"

@State(name = "KeymapManager", storages = arrayOf(Storage(value = "keymap.xml", roamingType = RoamingType.PER_OS)), additionalExportFile = KEYMAPS_DIR_PATH)
class KeymapManagerImpl(defaultKeymap: DefaultKeymap, factory: SchemeManagerFactory) : KeymapManagerEx(), PersistentStateComponent<Element> {
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<KeymapManagerListener>()
  private val boundShortcuts = THashMap<String, String>()
  private val schemeManager: SchemeManager<Keymap>

  init {
    schemeManager = factory.create(KEYMAPS_DIR_PATH, object : LazySchemeProcessor<Keymap, KeymapImpl>() {
      override fun createScheme(dataHolder: SchemeDataHolder<KeymapImpl>,
                                name: String,
                                attributeProvider: Function<String, String?>,
                                isBundled: Boolean) = KeymapImpl(name, dataHolder)
      override fun onCurrentSchemeSwitched(oldScheme: Keymap?, newScheme: Keymap?) {
        for (listener in listeners) {
          listener.activeKeymapChanged(newScheme)
        }
      }

      override fun reloaded(schemeManager: SchemeManager<Keymap>, schemes: Collection<Keymap>) {
        if (schemeManager.currentScheme == null) {
          // listeners expect that event will be fired in EDT
          AppUIUtil.invokeOnEdt {
            schemeManager.setCurrentSchemeName(defaultKeymap.defaultKeymapName, true)
          }
        }
      }
    })

    val systemDefaultKeymap = if (WelcomeWizardUtil.getWizardMacKeymap() == null) defaultKeymap.defaultKeymapName else WelcomeWizardUtil.getWizardMacKeymap()
    for (keymap in defaultKeymap.keymaps) {
      schemeManager.addScheme(keymap)
      if (keymap.name == systemDefaultKeymap) {
        activeKeymap = keymap
      }
    }
    schemeManager.loadSchemes()

    ourKeymapManagerInitialized = true
  }

  companion object {
    @JvmField
    var ourKeymapManagerInitialized: Boolean = false
  }

  override fun getAllKeymaps() = getKeymaps(Conditions.alwaysTrue<Keymap>()).toTypedArray()

  fun getKeymaps(additionalFilter: Condition<Keymap>) = schemeManager.allSchemes.filter { !it.presentableName.startsWith("$") && additionalFilter.value(it)  }

  override fun getKeymap(name: String) = schemeManager.findSchemeByName(name)

  override fun getActiveKeymap() = schemeManager.currentScheme

  override fun setActiveKeymap(keymap: Keymap?) = schemeManager.setCurrent(keymap)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts.put(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts.remove(targetActionId)
  }

  override fun getBoundActions() = boundShortcuts.keys

  override fun getActionBinding(actionId: String): String? {
    var visited: MutableSet<String>? = null
    var id = actionId
    while (true) {
      val next = boundShortcuts.get(id) ?: break
      if (visited == null) {
        visited = SmartHashSet()
      }

      id = next
      if (!visited.add(id)) {
        break
      }
    }
    return if (id == actionId) null else id
  }

  override fun getSchemeManager() = schemeManager

  fun setKeymaps(keymaps: List<Keymap>, active: Keymap?, removeCondition: Condition<Keymap>?) {
    schemeManager.setSchemes(keymaps, active, removeCondition)
  }

  override fun getState(): Element {
    val result = Element("state")
    schemeManager.currentScheme?.let {
      if (it.name != DefaultKeymap.instance.defaultKeymapName) {
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
      schemeManager.currentSchemeName = activeKeymapName
    }
  }

  @Suppress("OverridingDeprecatedMember")
  override fun addKeymapManagerListener(listener: KeymapManagerListener) {
    pollQueue()
    listeners.add(listener)
  }

  @Suppress("DEPRECATION")
  override fun addKeymapManagerListener(listener: KeymapManagerListener, parentDisposable: Disposable) {
    pollQueue()
    listeners.add(listener)
    Disposer.register(parentDisposable, Disposable { removeKeymapManagerListener(listener) })
  }

  private fun pollQueue() {
    listeners.removeAll { it is WeakKeymapManagerListener && it.isDead }
  }

  @Suppress("OverridingDeprecatedMember")
  override fun removeKeymapManagerListener(listener: KeymapManagerListener) {
    pollQueue()
    listeners.remove(listener)
  }

  @Suppress("DEPRECATION")
  override fun addWeakListener(listener: KeymapManagerListener) {
    addKeymapManagerListener(WeakKeymapManagerListener(this, listener))
  }

  override fun removeWeakListener(listenerToRemove: KeymapManagerListener) {
    listeners.removeAll { it is WeakKeymapManagerListener && it.isWrapped(listenerToRemove) }
  }
}
