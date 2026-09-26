// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import org.jetbrains.annotations.ApiStatus

@ApiStatus.OverrideOnly
fun interface Parser {
  fun parse(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean
}