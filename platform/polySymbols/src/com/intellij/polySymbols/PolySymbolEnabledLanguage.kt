// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolEnabledLanguage private constructor() : MetaLanguage("PolySymbolEnabledLanguage") {

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
    val EP_NAME = ExtensionPointName<PolySymbolSupportInLanguageEP>("com.intellij.polySymbols.enableInLanguage")
  }

  @ApiStatus.Experimental
  class PolySymbolSupportInLanguageEP : BaseKeyedLazyInstance<String?>() {
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
