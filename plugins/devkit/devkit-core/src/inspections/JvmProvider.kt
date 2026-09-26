// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface JvmProvider {
  fun isApplicableForKotlin(): Boolean
}

fun <T: JvmProvider> getProvider(extension: LanguageExtension<T>, language: Language): T? {
  val providers = extension.allForLanguage(language)
  return when (providers.size) {
    0 -> null
    1 -> providers[0]
    else -> providers.find(JvmProvider::isApplicableForKotlin)
  }
}
