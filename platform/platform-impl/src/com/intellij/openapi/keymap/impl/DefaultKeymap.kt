// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import java.util.*

private val LOG = logger<DefaultKeymap>()

open class DefaultKeymap {
  internal val keymaps = ArrayList<Keymap>()

  private val nameToScheme = HashMap<String, Keymap>()

  companion object {
    @JvmStatic
    val instance: DefaultKeymap
      get() = service()

    @JvmStatic
    fun isBundledKeymapHidden(keymapName: String?): Boolean {
      return ((SystemInfo.isWindows || SystemInfo.isMac) && isKnownLinuxKeymap(keymapName)) || (!SystemInfo.isMac && isKnownMacOSKeymap(keymapName))
    }
  }

  init {
    val filterKeymaps = !ApplicationManager.getApplication().isHeadlessEnvironment
                        //&& Registry.`is`("keymap.current.os.only")
    val filteredBeans = mutableListOf<BundledKeymapBean>()

    var macosParentKeymapFound = false
    val macosBeans = if (SystemInfo.isMac) null else mutableListOf<BundledKeymapBean>()

    for (bean in BundledKeymapBean.EP_NAME.extensionList) {
      val plugin = bean.pluginDescriptor ?: continue
      val keymapName = bean.keymapName
      // filter out bundled keymaps for other systems, but allow them via non-bundled plugins
      // on non-macOS add non-bundled known macOS keymaps if the default macOS keymap is present
      if (filterKeymaps && plugin.isBundled && isBundledKeymapHidden(keymapName)) continue
      if (filterKeymaps && macosBeans != null && !plugin.isBundled && isKnownMacOSKeymap(keymapName)) {
        macosParentKeymapFound = macosParentKeymapFound || keymapName == KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
        macosBeans.add(bean)
      }
      else {
        filteredBeans.add(bean)
      }
    }
    if (macosParentKeymapFound && macosBeans != null) {
      filteredBeans.addAll(macosBeans)
    }

    for (bean in filteredBeans) {
      LOG.runAndLogException {
        loadKeymap(bean.keymapName, object : SchemeDataHolder<KeymapImpl> {
          override fun read(): Element {
            return bean.pluginDescriptor.pluginClassLoader.getResourceAsStream(bean.effectiveFile).use {
              JDOMUtil.load(it)
            }
          }
        }, bean.pluginDescriptor)
      }
    }
  }

  internal fun loadKeymap(keymapName: String,
                          dataHolder: SchemeDataHolder<KeymapImpl>,
                          plugin: PluginDescriptor): DefaultKeymapImpl {
    val keymap = when {
      keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP) -> MacOSDefaultKeymap(dataHolder, this, plugin)
      else -> DefaultKeymapImpl(dataHolder, this, plugin)
    }
    keymap.name = keymapName
    addKeymap(keymap)
    return keymap
  }

  private fun addKeymap(keymap: DefaultKeymapImpl) {
    keymaps.add(keymap)
    nameToScheme[keymap.name] = keymap
  }

  internal fun removeKeymap(keymapName: String) {
    val removed = nameToScheme.remove(keymapName)
    keymaps.remove(removed)
  }

  internal fun findScheme(name: String) = nameToScheme[name]

  open val defaultKeymapName: String
    get() = when {
      SystemInfo.isMac -> KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
      SystemInfo.isGNOME -> KeymapManager.GNOME_KEYMAP
      SystemInfo.isKDE -> KeymapManager.KDE_KEYMAP
      SystemInfo.isXWindow -> KeymapManager.X_WINDOW_KEYMAP
      else -> KeymapManager.DEFAULT_IDEA_KEYMAP
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): String {
    return when (val name = keymap.name) {
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> "macOS"
      KeymapManager.DEFAULT_IDEA_KEYMAP -> "Windows"
      KeymapManager.GNOME_KEYMAP -> "GNOME"
      KeymapManager.KDE_KEYMAP -> "KDE"
      KeymapManager.X_WINDOW_KEYMAP -> "XWin"
      KeymapManager.MAC_OS_X_KEYMAP -> "IntelliJ IDEA Classic" + (if (SystemInfo.isMac) "" else " (macOS)")
      "NetBeans 6.5" -> "NetBeans"
      else -> {
        val newName = name
          .removeSuffix(" (Mac OS X)")
          .removeSuffix(" OSX")
        when {
          newName === name -> name
          else -> "$newName (macOS)"
        }
          .removePrefix("${osName()}/")
      }
    }
  }
}

internal val BundledKeymapBean.effectiveFile: String
  get() = "keymaps/${file.replace("\$OS\$", osName())}"

internal val BundledKeymapBean.keymapName: String
  get() = FileUtil.getNameWithoutExtension(file).removePrefix("\$OS\$/")

private fun osName(): String = when {
  SystemInfo.isMac -> "macos"
  SystemInfo.isWindows -> "windows"
  SystemInfo.isLinux -> "linux"
  else -> "other"
}

private fun isKnownLinuxKeymap(keymapName: String?) = when (keymapName) {
  KeymapManager.X_WINDOW_KEYMAP, KeymapManager.GNOME_KEYMAP, KeymapManager.KDE_KEYMAP -> true
  else -> false
}

private fun isKnownMacOSKeymap(keymapName: String?) = when (keymapName) {
  KeymapManager.MAC_OS_X_KEYMAP, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP,
  "Eclipse (Mac OS X)", "Sublime Text (Mac OS X)", "Xcode", "ReSharper OSX", "Visual Studio OSX", "Visual Assist OSX", "VSCode OSX" -> true
  else -> false
}