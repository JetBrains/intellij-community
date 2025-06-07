// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionWithAny
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.JvmStatic

object ElementTypeConverters {
  private val cache = ConcurrentHashMap<Language, ElementTypeConverter>()

  @ApiStatus.Internal
  @JvmStatic
  val instance: LanguageExtension<ElementTypeConverterFactory> = object : LanguageExtensionWithAny<ElementTypeConverterFactory>("com.intellij.syntax.elementTypeConverter") {
    override fun clearCache() {
      super.clearCache()
      cache.clear()
    }
  }

  @JvmStatic
  fun getConverter(
    language: Language,
  ): ElementTypeConverter = cache.getOrPut(language) {
    inferConverter(language) ?: run {
      logger<ElementTypeConverters>().error("No ElementTypeConverter for language $language")
      elementTypeConverterOf()
    }
  }

  private fun inferConverter(language: Language): ElementTypeConverter? {
    val converterFactories = instance.allForLanguage(language)
    if (converterFactories.isEmpty()) return null
    if (converterFactories.size == 1) return converterFactories.single().getElementTypeConverter()

    return compositeElementTypeConverter(converterFactories.map { it.getElementTypeConverter() })
  }
}
