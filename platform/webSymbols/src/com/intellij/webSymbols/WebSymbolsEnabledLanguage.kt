// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WebSymbolsEnabledLanguage private constructor() : MetaLanguage("WebSymbolsEnabledLanguage") {

  var totalTime: Long = 0
  var count: Long = 0

  override fun matchesLanguage(language: Language): Boolean =
    language == ANY || Companion.EP_NAME.extensionList
      .any {
        when (val l = findLanguageByID(it.language)) {
          null -> false
          is MetaLanguage -> l.matchesLanguage(language)
          else -> language.isKindOf(l)
        }
      }

  private object Companion {
    val EP_NAME = ExtensionPointName<WebSymbolsSupportInLanguageEP>("com.intellij.webSymbols.enableInLanguage")
  }

  @ApiStatus.Experimental
  class WebSymbolsSupportInLanguageEP : BaseKeyedLazyInstance<String?>() {
    @Attribute("language")
    lateinit var language: String

    override fun getImplementationClassName(): String? {
      return null
    }

    override fun createInstance(
      componentManager: ComponentManager,
      pluginDescriptor: PluginDescriptor,
    ): String {
      return language
    }
  }
}
