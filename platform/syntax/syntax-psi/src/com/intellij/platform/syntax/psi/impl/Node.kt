// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTNode

/**
 * Base interface for nodes in light tree
 */
internal interface Node : LighterASTNode {
  fun tokenTextMatches(chars: CharSequence): Boolean
}