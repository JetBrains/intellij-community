// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.LookAheadLexer

internal class ADLexer : LookAheadLexer(FlexAdapter(_ADLexer()))