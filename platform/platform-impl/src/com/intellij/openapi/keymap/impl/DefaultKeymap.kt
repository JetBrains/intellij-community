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
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import org.jdom.Element
import java.util.function.BiConsumer

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
      return ((SystemInfoRt.isWindows || SystemInfoRt.isMac) && isKnownLinuxKeymap(keymapName)) || (!SystemInfoRt.isMac && isKnownMacOSKeymap(keymapName))
    }
  }

  init {
    val filterKeymaps = !ApplicationManager.getApplication().isHeadlessEnvironment
                        && System.getProperty("keymap.current.os.only", "true").toBoolean()
    val filteredBeans = mutableListOf<Pair<BundledKeymapBean, PluginDescriptor>>()

    var macosParentKeymapFound = false
    val macosBeans = if (SystemInfoRt.isMac) null else mutableListOf<Pair<BundledKeymapBean, PluginDescriptor>>()

    BundledKeymapBean.EP_NAME.processWithPluginDescriptor(BiConsumer { bean, pluginDescriptor ->
      val keymapName = bean.keymapName
      // filter out bundled keymaps for other systems, but allow them via non-bundled plugins
      // on non-macOS add non-bundled known macOS keymaps if the default macOS keymap is present
      if (filterKeymaps && pluginDescriptor.isBundled && isBundledKeymapHidden(keymapName)) {
        return@BiConsumer
      }
      if (filterKeymaps && macosBeans != null && !pluginDescriptor.isBundled && isKnownMacOSKeymap(keymapName)) {
        macosParentKeymapFound = macosParentKeymapFound || keymapName == KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
        macosBeans.add(Pair(bean, pluginDescriptor))
      }
      else {
        filteredBeans.add(Pair(bean, pluginDescriptor))
      }
    })
    if (macosParentKeymapFound && macosBeans != null) {
      filteredBeans.addAll(macosBeans)
    }

    for ((bean, pluginDescriptor) in filteredBeans) {
      LOG.runAndLogException {
        loadKeymap(bean.keymapName, object : SchemeDataHolder<KeymapImpl> {
          override fun read(): Element {
            return pluginDescriptor.pluginClassLoader.getResourceAsStream(bean.effectiveFile).use {
              JDOMUtil.load(it)
            }
          }
        }, pluginDescriptor)
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
      SystemInfoRt.isMac -> KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
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
      KeymapManager.MAC_OS_X_KEYMAP -> "IntelliJ IDEA Classic" + (if (SystemInfoRt.isMac) "" else " (macOS)")
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
  get() = FileUtilRt.getNameWithoutExtension(file).removePrefix("\$OS\$/")

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
  KeymapManager.MAC_OS_X_KEYMAP, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP, "macOS System Shortcuts",
  "Eclipse (Mac OS X)", "Sublime Text (Mac OS X)", "Xcode", "ReSharper OSX",
  "Visual Studio OSX", "Visual Assist OSX", "Visual Studio for Mac", "VSCode OSX", "QtCreator (Mac OS X)" -> true
  else -> false
}