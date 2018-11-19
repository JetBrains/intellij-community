// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute

/**
 * @author yole
 */
class RegistryKeyBean {
  @JvmField @Attribute("key") val key: String = ""
  @JvmField @Attribute("description") val description: String = ""
  @JvmField @Attribute("defaultValue") val defaultValue: String = ""
  @JvmField @Attribute("restartRequired") val restartRequired: Boolean = false

  companion object {
    val EP_NAME = ExtensionPointName.create<RegistryKeyBean>("com.intellij.registryKey")
  }
}

class RegistryExtensionCollector {
  init {
    for (extension in RegistryKeyBean.EP_NAME.extensions) {
      Registry.addKey(extension.key, extension.description, extension.defaultValue, extension.restartRequired)
    }
  }
}
