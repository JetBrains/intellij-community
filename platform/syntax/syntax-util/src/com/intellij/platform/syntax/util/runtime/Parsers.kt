// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import kotlin.jvm.JvmField

@JvmField
val TOKEN_ADVANCER: Parser = Parser { runtime, level -> runtime.advanceToken(level) }

@JvmField
val TRUE_CONDITION: Parser = Parser { _, _ -> true }
