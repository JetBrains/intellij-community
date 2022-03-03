// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.IdeBundle
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.actions.CtrlYActionChooser
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.AppUIUtil
import com.intellij.util.ResourceUtil
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import java.util.function.Function
import java.util.function.Predicate

const val KEYMAPS_DIR_PATH = "keymaps"

private const val ACTIVE_KEYMAP = "active_keymap"
private const val NAME_ATTRIBUTE = "name"

@State(name = "KeymapManager", storages = [(Storage(value = "keymap.xml", roamingType = RoamingType.PER_OS))],
       additionalExportDirectory = KEYMAPS_DIR_PATH,
       category = SettingsCategory.KEYMAP)
class KeymapManagerImpl : KeymapManagerEx(), PersistentStateComponent<Element> {
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<KeymapManagerListener>()
  private val boundShortcuts = HashMap<String, String>()
  private val schemeManager: SchemeManager<Keymap>

  companion object {
    @JvmStatic
    var isKeymapManagerInitialized = false
      private set
  }

  init {
    schemeManager = SchemeManagerFactory.getInstance().create(KEYMAPS_DIR_PATH, object : LazySchemeProcessor<Keymap, KeymapImpl>() {
      override fun createScheme(dataHolder: SchemeDataHolder<KeymapImpl>,
                                name: String,
                                attributeProvider: Function<in String, String?>,
                                isBundled: Boolean) = KeymapImpl(name, dataHolder)
      override fun onCurrentSchemeSwitched(oldScheme: Keymap?,
                                           newScheme: Keymap?,
                                           processChangeSynchronously: Boolean) {
        fireActiveKeymapChanged(newScheme, activeKeymap)
      }

      override fun reloaded(schemeManager: SchemeManager<Keymap>, schemes: Collection<Keymap>) {
        if (schemeManager.activeScheme == null) {
          // listeners expect that event will be fired in EDT
          AppUIUtil.invokeOnEdt {
            schemeManager.setCurrentSchemeName(DefaultKeymap.getInstance().defaultKeymapName, true)
          }
        }
      }
    })

    val defaultKeymapManager = DefaultKeymap.getInstance()
    val systemDefaultKeymap = WelcomeWizardUtil.getWizardMacKeymap() ?: defaultKeymapManager.defaultKeymapName
    for (keymap in defaultKeymapManager.keymaps) {
      schemeManager.addScheme(keymap)
      if (keymap.name == systemDefaultKeymap) {
        schemeManager.setCurrent(keymap, notify = false)
      }
    }
    schemeManager.loadSchemes()

    isKeymapManagerInitialized = true

    if (ConfigImportHelper.isNewUser()) {
      CtrlYActionChooser.askAboutShortcut()
    }

    fun removeKeymap(keymapName: String) {
      val isCurrent = schemeManager.activeScheme?.name.equals(keymapName)
      val keymap = schemeManager.removeScheme(keymapName)
      if (keymap != null) {
        fireKeymapRemoved(keymap)
      }
      DefaultKeymap.getInstance().removeKeymap(keymapName)
      if (isCurrent) {
        val activeKeymap = schemeManager.activeScheme
                           ?: schemeManager.findSchemeByName(DefaultKeymap.getInstance().defaultKeymapName)
                           ?: schemeManager.findSchemeByName(KeymapManager.DEFAULT_IDEA_KEYMAP)
        schemeManager.setCurrent(activeKeymap, notify = true, processChangeSynchronously = true)
        fireActiveKeymapChanged(activeKeymap, activeKeymap)
      }
    }

    BundledKeymapBean.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<BundledKeymapBean> {
      override fun extensionAdded(ep: BundledKeymapBean, pluginDescriptor: PluginDescriptor) {
        val keymapName = getKeymapName(ep)
        //if (!SystemInfo.isMac &&
        //    keymapName != KeymapManager.MAC_OS_X_KEYMAP &&
        //    keymapName != KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP &&
        //    DefaultKeymap.isBundledKeymapHidden(keymapName) &&
        //    schemeManager.findSchemeByName(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP) == null) return
        val keymap = DefaultKeymap.getInstance().loadKeymap(keymapName, object : SchemeDataHolder<KeymapImpl> {
          override fun read(): Element {
            return JDOMUtil.load(ResourceUtil.getResourceAsBytes(getEffectiveFile(ep), pluginDescriptor.classLoader))
          }
        }, pluginDescriptor)
        schemeManager.addScheme(keymap)
        fireKeymapAdded(keymap)
        // do no set current keymap here, consider: multi-keymap plugins, parent keymaps loading
      }

      override fun extensionRemoved(ep: BundledKeymapBean, pluginDescriptor: PluginDescriptor) {
        removeKeymap(getKeymapName(ep))
      }
    }, null)
  }

  private fun fireKeymapAdded(keymap: Keymap) {
    ApplicationManager.getApplication().messageBus.syncPublisher(KeymapManagerListener.TOPIC).keymapAdded(keymap)
    for (listener in listeners) {
      listener.keymapAdded(keymap)
    }
  }

  private fun fireKeymapRemoved(keymap: Keymap) {
    ApplicationManager.getApplication().messageBus.syncPublisher(KeymapManagerListener.TOPIC).keymapRemoved(keymap)
    for (listener in listeners) {
      listener.keymapRemoved(keymap)
    }
  }

  private fun fireActiveKeymapChanged(newScheme: Keymap?, activeKeymap: Keymap?) {
    ApplicationManager.getApplication().messageBus.syncPublisher(KeymapManagerListener.TOPIC).activeKeymapChanged(activeKeymap)
    for (listener in listeners) {
      listener.activeKeymapChanged(newScheme)
    }
  }

  override fun getAllKeymaps(): Array<Keymap> = getKeymaps(null).toTypedArray()

  fun getKeymaps(additionalFilter: Predicate<Keymap>?): List<Keymap> {
    return schemeManager.allSchemes.filter { !it.presentableName.startsWith("$") && (additionalFilter == null || additionalFilter.test(it)) }
  }

  override fun getKeymap(name: String): Keymap? = schemeManager.findSchemeByName(name)

  override fun getActiveKeymap(): Keymap {
    return schemeManager.activeScheme
           ?: schemeManager.findSchemeByName(DefaultKeymap.getInstance().defaultKeymapName)
           ?: schemeManager.findSchemeByName(KeymapManager.DEFAULT_IDEA_KEYMAP)!!
  }

  override fun setActiveKeymap(keymap: Keymap) {
    schemeManager.setCurrent(keymap)
  }

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts.put(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts.remove(targetActionId)
  }

  override fun getBoundActions(): MutableSet<String> = boundShortcuts.keys

  override fun getActionBinding(actionId: String): String? {
    var visited: MutableSet<String>? = null
    var id = actionId
    while (true) {
      val next = boundShortcuts.get(id) ?: break
      if (visited == null) {
        visited = HashSet()
      }

      id = next
      if (!visited.add(id)) {
        break
      }
    }
    return if (id == actionId) null else id
  }

  override fun getSchemeManager(): SchemeManager<Keymap> = schemeManager

  fun setKeymaps(keymaps: List<Keymap>, active: Keymap?, removeCondition: Predicate<Keymap>?) {
    schemeManager.setSchemes(keymaps, active, removeCondition)
    fireActiveKeymapChanged(active, activeKeymap)
  }

  override fun getState(): Element {
    val result = Element("state")
    schemeManager.activeScheme?.let {
      if (it.name != DefaultKeymap.getInstance().defaultKeymapName) {
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
      if (schemeManager.currentSchemeName != activeKeymapName) {
        notifyAboutMissingKeymap(activeKeymapName, IdeBundle.message("notification.content.cannot.find.keymap", activeKeymapName), false)
      }
    }
  }

  @Suppress("DEPRECATION", "OverridingDeprecatedMember")
  override fun addKeymapManagerListener(listener: KeymapManagerListener, parentDisposable: Disposable) {
    pollQueue()
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(KeymapManagerListener.TOPIC, listener)
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
    pollQueue()
    listeners.add(WeakKeymapManagerListener(this, listener))
  }

  override fun removeWeakListener(listenerToRemove: KeymapManagerListener) {
    listeners.removeAll { it is WeakKeymapManagerListener && it.isWrapped(listenerToRemove) }
  }

  internal fun fireShortcutChanged(keymap: Keymap, actionId: String) {
    ApplicationManager.getApplication().messageBus.syncPublisher(KeymapManagerListener.TOPIC).shortcutChanged(keymap, actionId)
  }
}

val keymapComparator: Comparator<Keymap?> by lazy {
  val defaultKeymapName = DefaultKeymap.getInstance().defaultKeymapName
  Comparator { keymap1, keymap2 ->
    if (keymap1 === keymap2) return@Comparator 0
    if (keymap1 == null) return@Comparator - 1
    if (keymap2 == null) return@Comparator 1

    val parent1 = (if (!keymap1.canModify()) null else keymap1.parent) ?: keymap1
    val parent2 = (if (!keymap2.canModify()) null else keymap2.parent) ?: keymap2
    if (parent1 === parent2) {
      when {
        !keymap1.canModify() -> - 1
        !keymap2.canModify() -> 1
        else -> compareByName(keymap1, keymap2, defaultKeymapName)
      }
    }
    else {
      compareByName(parent1, parent2, defaultKeymapName)
    }
  }
}

private fun compareByName(keymap1: Keymap, keymap2: Keymap, defaultKeymapName: String): Int {
  return when (defaultKeymapName) {
    keymap1.name -> -1
    keymap2.name -> 1
    else -> NaturalComparator.INSTANCE.compare(keymap1.presentableName, keymap2.presentableName)
  }
}