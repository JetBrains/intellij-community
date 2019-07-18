// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*

private val LOG = logger<DefaultKeymap>()

open class DefaultKeymap @JvmOverloads constructor(providers: List<BundledKeymapProvider> = BundledKeymapProvider.EP_NAME.extensionList) {
  private val myKeymaps = ArrayList<Keymap>()

  private val nameToScheme = THashMap<String, Keymap>()

  init {
    for (provider in providers) {
      for (fileName in provider.keymapFileNames) {
        LOG.runAndLogException {
          loadKeymapsFromElement(object: SchemeDataHolder<KeymapImpl> {
            override fun read() = provider.load(fileName) { JDOMUtil.load(it) }

            override fun updateDigest(scheme: KeymapImpl) {
            }

            override fun updateDigest(data: Element?) {
            }
          }, provider.getKeyFromFileName(fileName), provider.javaClass)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: DefaultKeymap
      get() = service()

    @JvmStatic
    fun matchesPlatform(keymap: Keymap): Boolean {
      return when (keymap.name) {
        KeymapManager.DEFAULT_IDEA_KEYMAP -> SystemInfo.isWindows
        KeymapManager.MAC_OS_X_KEYMAP, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> SystemInfo.isMac
        KeymapManager.X_WINDOW_KEYMAP, KeymapManager.GNOME_KEYMAP, KeymapManager.KDE_KEYMAP -> SystemInfo.isXWindow
        else -> true
      }
    }
  }

  private fun loadKeymapsFromElement(dataHolder: SchemeDataHolder<KeymapImpl>,
                                     keymapName: String,
                                     providerClass: Class<BundledKeymapProvider>) {
    val keymap =
      if (keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP)) MacOSDefaultKeymap(dataHolder, this, providerClass)
      else DefaultKeymapImpl(dataHolder, this, providerClass)
    keymap.name = keymapName
    myKeymaps.add(keymap)
    nameToScheme.put(keymapName, keymap)
  }

  val keymaps: List<Keymap>
    get() = myKeymaps.toList()

  internal fun findScheme(name: String) = nameToScheme.get(name)

  open val defaultKeymapName: String
    get() = when {
      SystemInfo.isMac -> KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
      SystemInfo.isGNOME -> KeymapManager.GNOME_KEYMAP
      SystemInfo.isKDE -> KeymapManager.KDE_KEYMAP
      SystemInfo.isXWindow -> KeymapManager.X_WINDOW_KEYMAP
      else -> KeymapManager.DEFAULT_IDEA_KEYMAP
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): String {
    // Netbeans keymap is no longer for version 6.5, but we need to keep the id
    return when (val name = keymap.name) {
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> "Default for macOS"
      KeymapManager.DEFAULT_IDEA_KEYMAP -> "Default for Windows"
      KeymapManager.MAC_OS_X_KEYMAP -> "IntelliJ IDEA Classic" + (if (SystemInfo.isMac) "" else " (macOS)")
      "NetBeans 6.5" -> "NetBeans"
      else -> {
        val newName = name.removeSuffix(" (Mac OS X)").removeSuffix(" OSX")
        when {
          newName === name -> name
          else -> "$newName (macOS)"
        }
      }
    }
  }
}
