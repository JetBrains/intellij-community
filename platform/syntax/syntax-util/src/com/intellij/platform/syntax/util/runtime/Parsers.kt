// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.runtime

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmField

@ApiStatus.Experimental
@JvmField
val TOKEN_ADVANCER: Parser = Parser { parserRuntime: SyntaxGeneratedParserRuntime, level: Int -> parserRuntime.advanceToken(level) }

@ApiStatus.Experimental
@JvmField
val TRUE_CONDITION: Parser = Parser { parserRuntime: SyntaxGeneratedParserRuntime, level: Int -> true }
