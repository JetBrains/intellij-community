// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.LanguageExtension
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import kotlin.jvm.JvmStatic

/**
 * Extension providing access to [com.intellij.platform.syntax.LanguageSyntaxDefinition]s
 */
class LanguageSyntaxDefinitions : LanguageExtension<LanguageSyntaxDefinition>("com.intellij.syntax.syntaxDefinition") {
  companion object {
    @JvmStatic
    val INSTANCE: LanguageSyntaxDefinitions = LanguageSyntaxDefinitions()
  }
}