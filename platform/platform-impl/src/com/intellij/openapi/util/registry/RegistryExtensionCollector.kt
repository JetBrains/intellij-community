// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Registers custom key for [Registry].
 */
class RegistryKeyBean : PluginAware {
  companion object {
    // Since the XML parser removes all the '\n' chars joining indented lines together,
    // we can't really tell whether multiple whitespaces actually refer to indentation spaces or just regular ones.
    @NonNls
    @JvmStatic
    private val CONSECUTIVE_SPACES_REGEX = """\s{2,}""".toRegex()

    @JvmStatic
    private val pendingRemovalKeys = HashSet<String>()

    @JvmStatic
    internal fun addKeysFromPlugins() {
      val epName = ExtensionPointName<RegistryKeyBean>("com.intellij.registryKey")

      Registry.addKeys(epName.iterable.map { createRegistryKeyDescriptor(it) })

      epName.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean>, ExtensionPointPriorityListener {
        override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          Registry.addKeys(listOf(createRegistryKeyDescriptor(extension)))
        }
      }, null)

      epName.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean> {
        override fun extensionRemoved(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          pendingRemovalKeys.add(extension.key)
        }
      }, null)

      ApplicationManager.getApplication().messageBus.connect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          for (pendingRemovalKey in pendingRemovalKeys) {
            Registry.removeKey(pendingRemovalKey)
          }
          pendingRemovalKeys.clear()
        }
      })
    }

    @JvmStatic
    private fun createRegistryKeyDescriptor(extension: RegistryKeyBean): RegistryKeyDescriptor {
      val pluginId = extension.descriptor?.pluginId?.idString
      return RegistryKeyDescriptor(extension.key,
                                   StringUtil.unescapeStringCharacters(extension.description.replace(CONSECUTIVE_SPACES_REGEX, " ")),
                                   extension.defaultValue, extension.restartRequired,
                                   pluginId)
    }
  }

  @JvmField
  @Attribute("key")
  @RequiredElement
  val key = ""

  @JvmField
  @Attribute("description")
  @RequiredElement
  @Nls(capitalization = Nls.Capitalization.Sentence)
  val description = ""

  @JvmField
  @Attribute("defaultValue")
  @RequiredElement(allowEmpty = true)
  val defaultValue = ""

  @JvmField
  @Attribute("restartRequired")
  val restartRequired = false

  @Transient
  private var descriptor: PluginDescriptor? = null

  @Transient
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.descriptor = pluginDescriptor
  }
}