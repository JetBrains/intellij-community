// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.ResourceUtil
import org.jdom.Element
import java.util.function.BiConsumer

open class DefaultKeymap {
  internal val keymaps: MutableList<Keymap> = ArrayList()

  private val nameToScheme = HashMap<String, Keymap>()

  companion object {
    @JvmStatic
    fun getInstance(): DefaultKeymap = service()

    fun isBundledKeymapHidden(keymapName: String?): Boolean {
      return ((SystemInfoRt.isWindows || SystemInfoRt.isMac) &&
              isKnownLinuxKeymap(keymapName)) || (!SystemInfoRt.isMac && isKnownMacOSKeymap(keymapName))
    }
  }

  init {
    val filterKeymaps = !ApplicationManager.getApplication().isHeadlessEnvironment
                        && System.getProperty("keymap.current.os.only", "true").toBoolean()
    val filteredBeans = LinkedHashMap<BundledKeymapBean, PluginDescriptor>()

    var macosParentKeymapFound = false
    val macOsBeans = if (SystemInfoRt.isMac) null else LinkedHashMap<BundledKeymapBean, PluginDescriptor>()

    BundledKeymapBean.EP_NAME.processWithPluginDescriptor(BiConsumer { bean, pluginDescriptor ->
      val keymapName = getKeymapName(bean)
      // filter out bundled keymaps for other systems, but allow them via non-bundled plugins
      // on non-macOS add non-bundled known macOS keymaps if the default macOS keymap is present
      if (!filterKeymaps || !pluginDescriptor.isBundled || !isBundledKeymapHidden(keymapName)) {
        val isMacOsBean = filterKeymaps
                          && !pluginDescriptor.isBundled
                          && macOsBeans != null
                          && isKnownMacOSKeymap(keymapName)

        if (isMacOsBean) {
          macosParentKeymapFound = macosParentKeymapFound || keymapName == KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
        }

        (if (isMacOsBean) macOsBeans!! else filteredBeans).put(bean, pluginDescriptor)
      }
    })
    if (macosParentKeymapFound && macOsBeans != null) {
      filteredBeans.putAll(macOsBeans)
    }

    for ((bean, pluginDescriptor) in filteredBeans) {
      runCatching {
        loadKeymap(getKeymapName(bean), object : SchemeDataHolder<KeymapImpl> {
          override fun read(): Element {
            val effectiveFile = getEffectiveFile(bean)
            // java plugin defines keymap that located in a core plugin - so, we must check parents
            val data = ResourceUtil.getResourceAsBytes(effectiveFile, pluginDescriptor.classLoader, true)
            if (data == null) {
              throw PluginException("Cannot find $effectiveFile", pluginDescriptor.pluginId)
            }
            return JDOMUtil.load(data)
          }
        }, pluginDescriptor)
      }.getOrLogException(logger<DefaultKeymap>())
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

internal fun getEffectiveFile(bean: BundledKeymapBean) = "keymaps/${bean.file.replace("\$OS\$", osName())}"

internal fun getKeymapName(bean: BundledKeymapBean) = FileUtilRt.getNameWithoutExtension(bean.file).removePrefix("\$OS\$/")

private fun osName(): String {
  return when {
    SystemInfoRt.isMac -> "macos"
    SystemInfoRt.isWindows -> "windows"
    SystemInfoRt.isLinux -> "linux"
    else -> "other"
  }
}

private fun isKnownLinuxKeymap(keymapName: String?): Boolean {
  return when (keymapName) {
    KeymapManager.X_WINDOW_KEYMAP, KeymapManager.GNOME_KEYMAP, KeymapManager.KDE_KEYMAP -> true
    else -> false
  }
}

private fun isKnownMacOSKeymap(keymapName: String?): Boolean {
  return when (keymapName) {
    KeymapManager.MAC_OS_X_KEYMAP, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP, "macOS System Shortcuts",
    "Eclipse (Mac OS X)", "Sublime Text (Mac OS X)", "Xcode", "ReSharper OSX",
    "Visual Studio OSX", "Visual Assist OSX", "Visual Studio for Mac", "VSCode OSX", "QtCreator (Mac OS X)" -> true
    else -> false
  }
}