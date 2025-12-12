// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface Hook<T> {
  @Contract("_,null,_->null")
  fun run(parserRuntime: SyntaxGeneratedParserRuntime, marker: SyntaxTreeBuilder.Marker?, param: T): SyntaxTreeBuilder.Marker?
}