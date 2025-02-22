// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.runtime

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder

interface CompletionState : Function1<Any, String?> {
  fun convertItem(any: Any) : String
  fun prefixMatches(builder: SyntaxTreeBuilder, string: String) : Boolean
  fun prefixMatches(prefix: String, variant: String): Boolean
  fun addItem(builder: SyntaxTreeBuilder, string: String)
  val items : Collection<String?>
}