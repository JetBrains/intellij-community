// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.loadElement
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*

private val LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.DefaultKeymap")

open class DefaultKeymap {
  private val myKeymaps = ArrayList<Keymap>()

  private val nameToScheme = THashMap<String, Keymap>()

  protected open val providers: Array<BundledKeymapProvider>
    get() = Extensions.getExtensions(BundledKeymapProvider.EP_NAME)

  init {
    for (provider in providers) {
      for (fileName in provider.keymapFileNames) {
        // backward compatibility (no external usages of BundledKeymapProvider, but maybe it is not published to plugin manager)
        val key = when (fileName) {
          "Keymap_Default.xml" -> "\$default.xml"
          "Keymap_Mac.xml" -> "Mac OS X 10.5+.xml"
          "Keymap_MacClassic.xml" -> "Mac OS X.xml"
          "Keymap_GNOME.xml" -> "Default for GNOME.xml"
          "Keymap_KDE.xml" -> "Default for KDE.xml"
          "Keymap_XWin.xml" -> "Default for XWin.xml"
          "Keymap_EclipseMac.xml" -> "Eclipse (Mac OS X).xml"
          "Keymap_Eclipse.xml" -> "Eclipse.xml"
          "Keymap_Emacs.xml" -> "Emacs.xml"
          "JBuilderKeymap.xml" -> "JBuilder.xml"
          "Keymap_Netbeans.xml" -> "NetBeans 6.5.xml"
          "Keymap_ReSharper_OSX.xml" -> "ReSharper OSX.xml"
          "Keymap_ReSharper.xml" -> "ReSharper.xml"
          "RM_TextMateKeymap.xml" -> "TextMate.xml"
          "Keymap_Xcode.xml" -> "Xcode.xml"
          else -> fileName
        }

        LOG.runAndLogException {
          loadKeymapsFromElement(object: SchemeDataHolder<KeymapImpl> {
            override fun read() = provider.load(key) { loadElement(it) }

            override fun updateDigest(scheme: KeymapImpl) {
            }

            override fun updateDigest(data: Element?) {
            }
          }, provider.getKeyFromFileName(fileName))
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
        KeymapManager.X_WINDOW_KEYMAP, KeymapManager.GNOME_KEYMAP, KeymapManager.KDE_KEYMAP -> SystemInfo.isXWindow
        else -> true
      }
    }
  }

  private fun loadKeymapsFromElement(dataHolder: SchemeDataHolder<KeymapImpl>, keymapName: String) {
    val keymap = if (keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP)) MacOSDefaultKeymap(dataHolder, this) else DefaultKeymapImpl(dataHolder, this)
    keymap.name = keymapName
    myKeymaps.add(keymap)
    nameToScheme.put(keymapName, keymap)
  }

  val keymaps: Array<Keymap>
    get() = myKeymaps.toTypedArray()

  internal fun findScheme(name: String) = nameToScheme.get(name)

  open val defaultKeymapName: String
    get() = when {
      SystemInfo.isMac -> KeymapManager.MAC_OS_X_KEYMAP
      SystemInfo.isGNOME -> KeymapManager.GNOME_KEYMAP
      SystemInfo.isKDE -> KeymapManager.KDE_KEYMAP
      SystemInfo.isXWindow -> KeymapManager.X_WINDOW_KEYMAP
      else -> KeymapManager.DEFAULT_IDEA_KEYMAP
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): String {
    val name = keymap.name

    // Netbeans keymap is no longer for version 6.5, but we need to keep the id
    if (name == "NetBeans 6.5") {
      return "NetBeans"
    }

    return if (KeymapManager.DEFAULT_IDEA_KEYMAP == name) "Default" else name
  }
}
