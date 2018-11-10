// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_SEMI
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespacesAndComments

internal class GrTraditionalForClauseImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrTraditionalForClause {

  override fun toString(): String = "Traditional FOR clause"

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitTraditionalForClause(this)

  override fun getDeclaredVariables(): Array<out GrVariable> {
    val declaration = initialization as? GrVariableDeclaration
    return declaration?.variables ?: GrVariable.EMPTY_ARRAY
  }

  override fun getInitialization(): GrCondition? = firstChild as? GrCondition

  override fun getCondition(): GrExpression? {
    val firstSemicolon = findChildByType<PsiElement>(T_SEMI)
    val afterSemicolon = skipWhitespacesAndComments(firstSemicolon?.nextSibling, true)
    return afterSemicolon as? GrExpression
  }

  override fun getUpdate(): GrExpressionList? = lastChild as? GrExpressionList

  override fun processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement?,
                                   place: PsiElement): Boolean {
    val initialization = initialization ?: return true
    return lastParent === initialization ||
           initialization.processDeclarations(processor, state, null, place)
  }
}
