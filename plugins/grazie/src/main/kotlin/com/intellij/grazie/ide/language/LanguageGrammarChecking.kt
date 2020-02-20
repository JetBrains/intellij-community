// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint

class LanguageGrammarChecking : LanguageExtensionPoint<GrammarCheckingStrategy>() {
  companion object : LanguageExtension<GrammarCheckingStrategy>("com.intellij.grazie.grammar.strategy")
}
