// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.NonNls
import java.util.*

object VcsLocaleHelper {
  val EN_UTF_LOCALE: Locale = Locale("en_US.UTF-8", "en_US.utf8")
  val C_UTF_LOCALE: Locale = Locale("C.UTF-8", "c.utf8")
  private val DEFAULT_LOCALE: Locale = EN_UTF_LOCALE

  private const val REGISTRY_KEY_SUFFIX: @NonNls String = ".executable.locale"

  fun getEnvFromRegistry(prefix: @NonNls String): Map<String, String>? {
    try {
      val value = Registry.stringValue(prefix + REGISTRY_KEY_SUFFIX)
      if (value.isEmpty()) return emptyMap() // let skip locale definition if needed

      return createEnvForLocale(value)
    }
    catch (e: MissingResourceException) {
      return null // vmoptions are not overridden, use defaults
    }
  }

  @JvmStatic
  fun getDefaultLocaleEnvironmentVars(prefix: @NonNls String): Map<String, String> {
    val userLocale = getEnvFromRegistry(prefix)
    if (userLocale != null) return userLocale

    return createEnvForLocale(DEFAULT_LOCALE.name)
  }

  fun findMatchingLocale(userLocale: @NonNls String, knownLocales: List<Locale>): Map<String, String>? {
    val matchedLocale = knownLocales.find { it.matches(userLocale) }
    if (matchedLocale == null) return null

    return createEnvForLocale(matchedLocale.name)
  }

  private fun createEnvForLocale(locale: @NonNls String): Map<String, String> {
    val envMap = LinkedHashMap<String, String>()
    envMap["LANGUAGE"] = "" //NON-NLS
    envMap["LC_ALL"] = locale //NON-NLS
    return envMap
  }

  class Locale(val name: @NonNls String, vararg val synonyms: @NonNls String) {
    fun matches(userLocale: @NonNls String): Boolean {
      return allSynonyms.any { it.equals(userLocale, ignoreCase = true) }
    }

    private val allSynonyms: Sequence<@NonNls String> get() = sequenceOf(name, *synonyms)
  }
}
