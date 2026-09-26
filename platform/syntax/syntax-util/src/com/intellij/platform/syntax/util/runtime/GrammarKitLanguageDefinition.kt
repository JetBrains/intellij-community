// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementType

interface GrammarKitLanguageDefinition : LanguageSyntaxDefinition {

  fun parse(elementType: SyntaxElementType, runtime: SyntaxGeneratedParserRuntime)

  fun getPairedBraces(): Collection<BracePair> = emptyList()
}