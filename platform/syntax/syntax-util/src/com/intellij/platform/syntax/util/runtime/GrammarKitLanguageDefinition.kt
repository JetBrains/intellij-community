// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.LanguageSyntaxDefinition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GrammarKitLanguageDefinition : LanguageSyntaxDefinition {
  fun getPairedBraces(): Collection<SyntaxGeneratedParserRuntime.BracePair> = emptyList()
}