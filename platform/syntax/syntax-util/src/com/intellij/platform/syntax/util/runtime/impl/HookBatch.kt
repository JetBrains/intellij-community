// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.runtime.Hook
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime

internal data class HookBatch<T>(
  val hook: Hook<T>,
  val param: T,
  val level: Int,
) {
  fun process(parserRuntime: SyntaxGeneratedParserRuntime, marker: SyntaxTreeBuilder.Marker?): SyntaxTreeBuilder.Marker? {
    return hook.run(parserRuntime, marker, param)
  }
}