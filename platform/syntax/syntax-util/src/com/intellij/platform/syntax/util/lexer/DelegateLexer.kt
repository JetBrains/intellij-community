// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.lexer.Lexer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class DelegateLexer(val delegate: Lexer) : Lexer by delegate
