// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder

interface CompletionVariantProvider {
  fun addCompletionVariantSmart(builder: SyntaxTreeBuilder, token: Any)
  fun addCompletionVariant(builder: SyntaxTreeBuilder, completionState: CompletionState?, o: Any)
  fun getCompletionState(): CompletionState?
}