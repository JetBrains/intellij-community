// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.UserDataHolder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.util.ThreeState
import com.intellij.util.TripleFunction
import com.intellij.util.diff.FlyweightCapableTreeStructure

interface PsiSyntaxBuilder : UserDataHolder {
  fun getSyntaxTreeBuilder(): SyntaxTreeBuilder

  fun getTreeBuilt(): ASTNode
  fun getLightTree(): FlyweightCapableTreeStructure<LighterASTNode>
  fun setDebugMode(value: Boolean)
  fun setCustomComparator(comparator: TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>)
}