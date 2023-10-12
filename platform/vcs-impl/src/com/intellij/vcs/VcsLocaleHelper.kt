// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.NonNls
import java.util.*

object VcsLocaleHelper {
  private val DEFAULT_EXECUTABLE_LOCALE_VALUE: @NonNls String = "en_US.UTF-8"
  private val REGISTRY_KEY_SUFFIX: @NonNls String = ".executable.locale"

  @JvmStatic
  fun getDefaultLocaleFromRegistry(prefix: @NonNls String): @NonNls String {
    try {
      return Registry.stringValue(prefix + REGISTRY_KEY_SUFFIX)
    }
    catch (e: MissingResourceException) {
      return DEFAULT_EXECUTABLE_LOCALE_VALUE
    }
  }

  @JvmStatic
  fun getDefaultLocaleEnvironmentVars(prefix: @NonNls String): Map<String, String> {
    val envMap = LinkedHashMap<String, String>()
    val defaultLocale = getDefaultLocaleFromRegistry(prefix)
    if (defaultLocale.isEmpty()) { // let skip locale definition if needed
      return envMap
    }

    envMap["LANGUAGE"] = "" //NON-NLS
    envMap["LC_ALL"] = defaultLocale //NON-NLS
    return envMap
  }
}
