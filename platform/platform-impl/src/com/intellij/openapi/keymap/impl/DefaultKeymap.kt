// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.ResourceUtil
import com.intellij.util.system.OS
import com.intellij.util.ui.UnixDesktopEnv
import org.jdom.Element

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

    BundledKeymapBean.EP_NAME.processWithPluginDescriptor { bean, pluginDescriptor ->
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
    }
    if (macosParentKeymapFound && macOsBeans != null) {
      filteredBeans.putAll(macOsBeans)
    }

    val reassignAltClickToMultipleCarets = ApplicationInfoEx.getInstanceEx().isReassignAltClickToMultipleCarets
    for ((bean, pluginDescriptor) in filteredBeans) {
      runCatching {
        val keymapName = getKeymapName(bean)
        loadKeymap(keymapName, object : SchemeDataHolder<KeymapImpl> {
          override fun read(): Element {
            val effectiveFile = getEffectiveFile(bean)
            // check parents because of IDEA-314393
            val data = ResourceUtil.getResourceAsBytes(effectiveFile, pluginDescriptor.classLoader, true)
            if (data == null) {
              throw PluginException("Cannot find $effectiveFile", pluginDescriptor.pluginId)
            }
            val element = JDOMUtil.load(data)
            if (reassignAltClickToMultipleCarets && keymapName == KeymapManager.DEFAULT_IDEA_KEYMAP) {
              swapAltClickWithAltShiftClick(element)
            }
            return element
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

  internal fun findScheme(name: String): Keymap? = nameToScheme[name]

  open val defaultKeymapName: String
    get() = when {
      OS.CURRENT == OS.macOS -> KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP
      UnixDesktopEnv.CURRENT == UnixDesktopEnv.GNOME -> KeymapManager.GNOME_KEYMAP
      UnixDesktopEnv.CURRENT == UnixDesktopEnv.KDE -> KeymapManager.KDE_KEYMAP
      OS.isGenericUnix() -> KeymapManager.X_WINDOW_KEYMAP
      else -> KeymapManager.DEFAULT_IDEA_KEYMAP
    }

  open fun getKeymapPresentableName(keymap: KeymapImpl): @NlsSafe String {
    return when (val name = keymap.name) {
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> "macOS"
      KeymapManager.DEFAULT_IDEA_KEYMAP -> "Windows"
      KeymapManager.GNOME_KEYMAP -> "GNOME"
      KeymapManager.KDE_KEYMAP -> "KDE"
      KeymapManager.X_WINDOW_KEYMAP -> "XWin"
      KeymapManager.MAC_OS_X_KEYMAP -> "IntelliJ IDEA Classic" + (if (SystemInfoRt.isMac) "" else " (macOS)")
      KeymapManager.NET_BEANS_6_5 -> "NetBeans"
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

  @Suppress("DialogTitleCapitalization") // the descriptions contain product names
  open fun getKeymapDescription(keymap: KeymapImpl): @NlsContexts.Tooltip String? {
    if (keymap !is DefaultKeymapImpl) {
      return null
    }
    return when (keymap.name) {
      KeymapManager.DEFAULT_IDEA_KEYMAP -> KeyMapBundle.message("keymap.\$default.description")
      KeymapManager.GNOME_KEYMAP -> KeyMapBundle.message("keymap.Default_for_GNOME.description")
      KeymapManager.KDE_KEYMAP -> KeyMapBundle.message("keymap.Default_for_KDE.description")
      KeymapManager.X_WINDOW_KEYMAP -> KeyMapBundle.message("keymap.Default_for_XWin.description")
      KeymapManager.ECLIPSE -> KeyMapBundle.message("keymap.Eclipse.description")
      KeymapManager.ECLIPSE_MAC_OS_X -> KeyMapBundle.message("keymap.Eclipse_(Mac_OS_X).description")
      KeymapManager.EMACS -> KeyMapBundle.message("keymap.Emacs.description")
      KeymapManager.MAC_OS_X_KEYMAP -> KeyMapBundle.message("keymap.Mac_OS_X.description")
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> KeyMapBundle.message("keymap.Mac_OS_X_10.5+.description")
      KeymapManager.MAC_OS_SYSTEM_SHORTCUTS -> KeyMapBundle.message("keymap.macOS_System_Shortcuts.description")
      KeymapManager.NET_BEANS_6_5 -> KeyMapBundle.message("keymap.NetBeans_6.5.description")
      KeymapManager.QT_CREATOR -> KeyMapBundle.message("keymap.QtCreator.description")
      KeymapManager.QT_CREATOR_MAC_OS_X -> KeyMapBundle.message("keymap.QtCreator_(Mac_OS_X).description")
      KeymapManager.RESHARPER -> KeyMapBundle.message("keymap.ReSharper.description")
      KeymapManager.RESHARPER_OSX -> KeyMapBundle.message("keymap.ReSharper_OSX.description")
      KeymapManager.SUBLIME_TEXT -> KeyMapBundle.message("keymap.Sublime_Text.description")
      KeymapManager.SUBLIME_TEXT_MAC_OS_X -> KeyMapBundle.message("keymap.Sublime_Text_(Mac_OS_X).description")
      KeymapManager.TEXT_MATE -> KeyMapBundle.message("keymap.TextMate.description")
      KeymapManager.VISUAL_ASSIST -> KeyMapBundle.message("keymap.Visual_Assist.description")
      KeymapManager.VISUAL_ASSIST_OSX -> KeyMapBundle.message("keymap.Visual_Assist_OSX.description")
      KeymapManager.VISUAL_STUDIO -> KeyMapBundle.message("keymap.Visual_Studio.description")
      KeymapManager.VISUAL_STUDIO_2022 -> KeyMapBundle.message("keymap.Visual_Studio_2022.description")
      KeymapManager.VISUAL_STUDIO_OSX -> KeyMapBundle.message("keymap.Visual_Studio_OSX.description")
      KeymapManager.VISUAL_STUDIO_FOR_MAC -> KeyMapBundle.message("keymap.Visual_Studio_for_Mac.description")
      KeymapManager.VSCODE -> KeyMapBundle.message("keymap.VSCode.description")
      KeymapManager.VSCODE_OSX -> KeyMapBundle.message("keymap.VSCode_OSX.description")
      KeymapManager.XCODE -> KeyMapBundle.message("keymap.Xcode.description")
      else -> null
    }
  }
}

/**
 * Swaps the `alt button1` and `alt shift button1` mouse shortcuts of every action in [keymap].
 *
 * A keystroke must match exactly. So `alt button1 doubleClick` and `ctrl alt button1` stay.
 * [ApplicationInfoEx.isReassignAltClickToMultipleCarets] enables the swap for the default keymap.
 */
internal fun swapAltClickWithAltShiftClick(keymap: Element) {
  for (action in keymap.getChildren("action")) {
    for (shortcut in action.getChildren("mouse-shortcut")) {
      when (shortcut.getAttributeValue("keystroke")) {
        "alt button1" -> shortcut.setAttribute("keystroke", "alt shift button1")
        "alt shift button1" -> shortcut.setAttribute("keystroke", "alt button1")
      }
    }
  }
}

internal fun getEffectiveFile(bean: BundledKeymapBean): String = "keymaps/${bean.file.replace("\$OS\$", osName())}"

internal fun getKeymapName(bean: BundledKeymapBean): String = FileUtilRt.getNameWithoutExtension(bean.file).removePrefix("\$OS\$/")

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
    KeymapManager.MAC_OS_X_KEYMAP,
    KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP,
    KeymapManager.MAC_OS_SYSTEM_SHORTCUTS,
    KeymapManager.ECLIPSE_MAC_OS_X,
    KeymapManager.SUBLIME_TEXT_MAC_OS_X,
    KeymapManager.XCODE,
    KeymapManager.RESHARPER_OSX,
    KeymapManager.VISUAL_STUDIO_OSX,
    KeymapManager.VISUAL_ASSIST_OSX,
    KeymapManager.VISUAL_STUDIO_FOR_MAC,
    KeymapManager.VSCODE_OSX,
    KeymapManager.QT_CREATOR_MAC_OS_X -> true
    else -> false
  }
}
