/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import java.net.URL
import java.util.*

private val LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.DefaultKeymap")
private val NAME_ATTRIBUTE = "name"

open class DefaultKeymap {
  private val myKeymaps = ArrayList<Keymap>()

  protected open val providers: Array<BundledKeymapProvider>
    get() = Extensions.getExtensions(BundledKeymapProvider.EP_NAME)

  init {
    for (provider in providers) {
      val fileNames = provider.keymapFileNames
      for (fileName in fileNames) {
        try {
          loadKeymapsFromElement(object: SchemeDataHolder<KeymapImpl> {
            override fun read() = JDOMUtil.loadResourceDocument(URL("file:///idea/" + fileName)).rootElement

            override fun updateDigest(scheme: KeymapImpl) {
            }

            override fun updateDigest(data: Element) {
            }
          }, fileName)
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: DefaultKeymap
      get() = ServiceManager.getService(DefaultKeymap::class.java)

    @JvmStatic
    fun matchesPlatform(keymap: Keymap): Boolean {
      val name = keymap.name
      return when (name) {
        KeymapManager.DEFAULT_IDEA_KEYMAP -> SystemInfo.isWindows
        KeymapManager.MAC_OS_X_KEYMAP, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> SystemInfo.isMac
        KeymapManager.X_WINDOW_KEYMAP, "Default for GNOME", KeymapManager.KDE_KEYMAP -> SystemInfo.isXWindow
        else -> true
      }
    }
  }

  private fun loadKeymapsFromElement(dataHolder: SchemeDataHolder<KeymapImpl>, keymapName: String) {
    myKeymaps.add(if (keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP)) MacOSDefaultKeymap(dataHolder) else DefaultKeymapImpl(dataHolder))
  }

  val keymaps: Array<Keymap>
    get() = myKeymaps.toTypedArray()

  open val defaultKeymapName: String
    get() {
      if (SystemInfo.isMac) {
        return KeymapManager.MAC_OS_X_KEYMAP
      }
      else if (SystemInfo.isXWindow) {
        if (SystemInfo.isKDE) {
          return KeymapManager.KDE_KEYMAP
        }
        else {
          return KeymapManager.X_WINDOW_KEYMAP
        }
      }
      else {
        return KeymapManager.DEFAULT_IDEA_KEYMAP
      }
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): String {
    val name = keymap.name

    // Netbeans keymap is no longer for version 6.5, but we need to keep the id
    if ("NetBeans 6.5" == name) {
      return "NetBeans"
    }

    return if (KeymapManager.DEFAULT_IDEA_KEYMAP == name) "Default" else name
  }
}
