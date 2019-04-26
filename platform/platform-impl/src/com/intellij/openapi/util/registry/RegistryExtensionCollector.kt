// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient

/**
 * @author yole
 */
class RegistryKeyBean : PluginAware {
  @JvmField @Attribute("key") val key: String = ""
  @JvmField @Attribute("description") val description: String = ""
  @JvmField @Attribute("defaultValue") val defaultValue: String = ""
  @JvmField @Attribute("restartRequired") val restartRequired: Boolean = false

  @Transient
  var pluginDescriptor: PluginDescriptor? = null
    private set

  @Transient
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor?) {
    this.pluginDescriptor = pluginDescriptor
  }

  companion object {
    val EP_NAME = ExtensionPointName<RegistryKeyBean>("com.intellij.registryKey")
  }
}

class RegistryExtensionCollector {
  init {
    for (extension in RegistryKeyBean.EP_NAME.extensionList) {
      val contributedByThirdParty = extension.pluginDescriptor?.let { !getPluginInfoById(it.pluginId).isSafeToReport() } ?: false
      Registry.addKey(extension.key, extension.description.unescapeString(), extension.defaultValue, extension.restartRequired,
                      contributedByThirdParty)
    }
  }

  private companion object {
    // Since the XML parser removes all the '\n' chars joining indented lines together,
    // we can't really tell whether multiple whitespaces actually refer to indentation spaces or just regular ones.
    val CONSECUTIVE_SPACES_REGEX = """\s{2,}""".toRegex()

    fun String.unescapeString(): String = StringUtil.unescapeStringCharacters(replace(CONSECUTIVE_SPACES_REGEX, " "))
  }
}