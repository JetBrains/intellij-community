// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.TokenSet.andNot
import com.intellij.psi.tree.TokenSet.orSet

operator fun TokenSet.plus(another: TokenSet): TokenSet = orSet(this, another)

operator fun TokenSet.minus(another: TokenSet): TokenSet = andNot(this, another)

@Suppress("FunctionName")
fun TokenSet(vararg tokens: IElementType): TokenSet = TokenSet.create(*tokens)
