// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import gnu.trove.THashMap
import java.util.*

private val LOG = logger<DefaultKeymap>()

open class DefaultKeymap {
  private val myKeymaps = ArrayList<Keymap>()

  private val nameToScheme = THashMap<String, Keymap>()

  companion object {
    @JvmStatic
    val instance: DefaultKeymap
      get() = service()

    @JvmStatic
    fun isBundledKeymapHidden(keymapName: String?): Boolean {
      if (SystemInfo.isWindows || SystemInfo.isMac) {
        if ("Default for XWin" == keymapName ||
            "Default for GNOME" == keymapName ||
            "Default for KDE" == keymapName)
          return true
      }
      return isBundledMacOSKeymap(keymapName)
    }

    private fun isBundledMacOSKeymap(keymapName: String?): Boolean {
      if (!SystemInfo.isMac) {
        if ("Mac OS X" == keymapName ||
            "Mac OS X 10.5+" == keymapName ||
            "Eclipse (Mac OS X)" == keymapName ||
            "Sublime Text (Mac OS X)" == keymapName) {
          return true
        }
      }
      return false
    }
  }

  init {
    val headless = ApplicationManager.getApplication().isHeadlessEnvironment
    loop@ for (bean in BundledKeymapBean.EP_NAME.extensionList) {
      val plugin = bean.pluginDescriptor ?: continue@loop
      val keymapName = bean.keymapName
      // add all OS-specific
      // filter out bundled keymaps for other systems, but allow them via non-bundled plugins
      // also skip non-bundled known macOS keymaps on non-macOS systems
      if (!(bean.file.contains("\$OS\$") ||
            headless ||
            !plugin.isBundled && !isBundledMacOSKeymap(keymapName) ||
            !isBundledKeymapHidden(keymapName))) continue@loop
      LOG.runAndLogException {
        loadKeymap(keymapName, object : SchemeDataHolder<KeymapImpl> {
          override fun read() = bean.pluginDescriptor.pluginClassLoader
            .getResourceAsStream(bean.effectiveFile).use { JDOMUtil.load(it) }
        }, bean.pluginDescriptor.pluginId)
      }
    }

    @Suppress("DEPRECATION")
    for (provider in BundledKeymapProvider.EP_NAME.extensionList) {
      for (fileName in provider.keymapFileNames) {
        val keymapName = provider.getKeyFromFileName(fileName)
        val pluginId = PluginManagerCore.getPluginOrPlatformByClassName(provider.javaClass.name)
        LOG.runAndLogException {
          loadKeymap(keymapName, object : SchemeDataHolder<KeymapImpl> {
            override fun read() = provider.load(fileName) { JDOMUtil.load(it) }
          }, pluginId)
        }
      }
    }
  }

  internal fun loadKeymap(keymapName: String,
                          dataHolder: SchemeDataHolder<KeymapImpl>,
                          pluginId: PluginId?): DefaultKeymapImpl {
    val keymap = when {
      keymapName.startsWith(KeymapManager.MAC_OS_X_KEYMAP) -> MacOSDefaultKeymap(dataHolder, this, pluginId)
      else -> DefaultKeymapImpl(dataHolder, this, pluginId)
    }
    keymap.name = keymapName
    addKeymap(keymap)
    return keymap
  }

  private fun addKeymap(keymap: DefaultKeymapImpl) {
    myKeymaps.add(keymap)
    nameToScheme[keymap.name] = keymap
  }

  internal fun removeKeymap(keymapName: String) {
    val removed = nameToScheme.remove(keymapName)
    myKeymaps.remove(removed)
  }

  val keymaps: List<Keymap>
    get() = myKeymaps.toList()

  internal fun findScheme(name: String) = nameToScheme[name]

  open val defaultKeymapName: String
    get() = when {
      SystemInfo.isMac -> KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
      SystemInfo.isGNOME -> KeymapManager.GNOME_KEYMAP
      SystemInfo.isKDE -> KeymapManager.KDE_KEYMAP
      SystemInfo.isXWindow -> KeymapManager.X_WINDOW_KEYMAP
      else -> KeymapManager.DEFAULT_IDEA_KEYMAP
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): String = when (val name = keymap.name) {
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

internal val BundledKeymapBean.effectiveFile: String
  get() = "/keymaps/${file.replace("\$OS\$", osName())}"
internal val BundledKeymapBean.keymapName: String
  get() = FileUtil.getNameWithoutExtension(file).removePrefix("\$OS\$/")

private fun osName(): String = when {
  SystemInfo.isMac -> "macos"
  SystemInfo.isWindows -> "windows"
  SystemInfo.isLinux -> "linux"
  else -> "other"
}
