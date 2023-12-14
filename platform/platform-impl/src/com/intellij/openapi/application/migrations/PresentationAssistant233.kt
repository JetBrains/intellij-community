// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.DisabledPluginsState.Companion.loadDisabledPlugins
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.Constants
import org.jdom.Attribute
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class PresentationAssistant233 {
  fun migratePlugin(options: PluginMigrationOptions) {
    val pluginId = "org.nik.presentation-assistant"
    if (StringUtil.compareVersionNumbers(options.currentProductVersion, "233") >= 0) {
      val pluginDescriptor = options.pluginsToMigrate.find { it.pluginId.idString == pluginId }
                             ?: options.pluginsToDownload.find { it.pluginId.idString == pluginId }
      options.pluginsToMigrate.removeIf { it.pluginId.idString == pluginId }
      options.pluginsToDownload.removeIf { it.pluginId.idString == pluginId }
      if (pluginDescriptor != null) {
        val pluginSettingsFile = options.oldConfigDir.resolve(PathManager.OPTIONS_DIRECTORY).resolve("presentation-assistant.xml")
        if (!pluginSettingsFile.exists() && !pluginDescriptor.isEnabled) return
        var newSettingsFile = options.newConfigDir.resolve(PathManager.OPTIONS_DIRECTORY).resolve("presentation-assistant-ij.xml")
        if (!newSettingsFile.exists()) {
          newSettingsFile = newSettingsFile.findOrCreateFile()
          JDOMUtil.write(Element("application").apply {
            children.add(Element("component").apply {
              setAttribute(Attribute("name", "PresentationAssistantIJ"))
            })
          }, newSettingsFile)
        }
        val applicationElement = JDOMUtil.load(newSettingsFile)
        val newComponent = applicationElement.getChild("component")
        var showActionsEnabled = true
        if (pluginSettingsFile.exists()) {
          val component = JDOMUtil.load(pluginSettingsFile).getChild("component")
          val componentOptions = JDOMUtil.getChildren(component, Constants.OPTION)
          if (componentOptions.isNotEmpty()) {
            for (option in componentOptions) {
              val attributeName = option.attributes.find { it.name == Constants.NAME }
              when (attributeName?.value) {
                "verticalAlignment" -> {
                  val verticalAlignment = getValue(option)?.let {
                    PopupVerticalAlignment.valueOf(it)
                  }
                  verticalAlignment?.id?.let {
                    setOption(newComponent, "verticalAlignment", it.toString())
                  }
                }
                "horizontalAlignment" -> {
                  val horizontalAlignment = getValue(option)?.let {
                    PopupHorizontalAlignment.valueOf(it)
                  }
                  horizontalAlignment?.id?.let {
                    setOption(newComponent, "horizontalAlignment", it.toString())
                  }
                }
                "hideDelay" -> {
                  val value = getValue(option)
                  value?.let { setOption(newComponent, "popupDuration", it) }
                }
                "mainKeymap" -> {
                  setKeymapValue(option, newComponent, "mainKeymapName", "mainKeymapLabel")
                }
                "alternativeKeymap" -> {
                  setKeymapValue(option, newComponent, "alternativeKeymapName", "alternativeKeymapLabel")
                  if (JDOMExternalizerUtil.readOption(newComponent, "alternativeKeymapName") != null) {
                    setOption(newComponent, "showAlternativeKeymap", "true")
                  }
                }
                "showActionDescriptions" -> {
                  showActionsEnabled = getValue(option)?.toBoolean() ?: true
                }
              }
            }
          }
        }
        val disabledPluginsFile: Path = options.oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
        val isPluginEnabled = !(if (Files.exists(disabledPluginsFile)) loadDisabledPlugins(disabledPluginsFile) else setOf())
          .contains(pluginDescriptor.pluginId)
        setOption(newComponent, "showActionDescriptions", (isPluginEnabled && showActionsEnabled).toString())
        JDOMUtil.write(applicationElement, newSettingsFile)
      }
    }
  }

  private fun setOption(component: Element, name: String, value: String) {
    val option = JDOMExternalizerUtil.readOption(component, name) ?: JDOMExternalizerUtil.writeOption(component, name)
    option.setAttribute(Attribute(Constants.VALUE, value))
  }

  private fun getValue(option: Element): String? {
    return option.attributes.find { it.name == Constants.VALUE }?.value
  }

  private fun setKeymapValue(option: Element, newComponent: Element, name: String, labelName: String) {
    val mainKeymapOptions = JDOMUtil.getChildren(option.getChild("KeymapDescription"), Constants.OPTION)
    mainKeymapOptions.find { it.attributes.any { attribute -> attribute.value == Constants.NAME } }?.let { nameOption ->
      getValue(nameOption)?.let { setOption(newComponent, name, it) }
      mainKeymapOptions.find { it.attributes.any { attribute -> attribute.value == "displayText" } }?.let {
        getValue(it)?.let { value -> setOption(newComponent, labelName, value) }
      }
    }
  }

}

enum class PopupHorizontalAlignment(val displayName: String, val id: Int) { LEFT("Left", 0), CENTER("Center", 1), RIGHT("Right", 2) }
enum class PopupVerticalAlignment(val displayName: String, val id: Int) { TOP("Top", 0), BOTTOM("Bottom", 2) }
