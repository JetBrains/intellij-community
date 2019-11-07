// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.Nls

private val EP_NAME = ExtensionPointName<RegistryKeyBean>("com.intellij.registryKey")

// Since the XML parser removes all the '\n' chars joining indented lines together,
// we can't really tell whether multiple whitespaces actually refer to indentation spaces or just regular ones.
private val CONSECUTIVE_SPACES_REGEX = """\s{2,}""".toRegex()

private fun String.unescapeString() = StringUtil.unescapeStringCharacters(replace(CONSECUTIVE_SPACES_REGEX, " "))

class RegistryKeyBean : PluginAware {
  companion object {
    @JvmStatic
    fun addKeysFromPlugins() {
      Registry.addKeys(EP_NAME.iterable.map { createRegistryKeyDescriptor(it) })

      EP_NAME.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean> {
        override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          Registry.addKeys(listOf(createRegistryKeyDescriptor(extension)))
        }

        override fun extensionRemoved(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          Registry.removeKey(extension.key)
        }
      }, null)
    }

    private fun createRegistryKeyDescriptor(extension: RegistryKeyBean): RegistryKeyDescriptor {
      val contributedByThirdParty = extension.descriptor?.let { !getPluginInfoByDescriptor(it).isSafeToReport() } ?: false
      return RegistryKeyDescriptor(extension.key, extension.description.unescapeString(), extension.defaultValue, extension.restartRequired,
                                   contributedByThirdParty)
    }
  }

  @JvmField
  @Attribute("key")
  val key = ""

  @JvmField
  @Attribute("description")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  val description = ""

  @JvmField
  @Attribute("defaultValue")
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