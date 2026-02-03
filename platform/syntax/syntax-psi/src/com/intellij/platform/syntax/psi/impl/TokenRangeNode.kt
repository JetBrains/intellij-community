// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTTokenNode

/**
 * A node in light tree
 * Represents a chameleon consisting several lexemes which does not support light parsing
 */
internal class TokenRangeNode : TokenRange(), LighterASTTokenNode