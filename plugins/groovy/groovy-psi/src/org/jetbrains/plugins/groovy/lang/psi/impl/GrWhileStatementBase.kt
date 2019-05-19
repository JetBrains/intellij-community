// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_LPAREN
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_RPAREN
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.replaceBody
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespacesAndComments

abstract class GrWhileStatementBase(node: ASTNode) : GroovyPsiElementImpl(node), GrLoopStatement {

  fun getLParenth(): PsiElement? = findChildByType(T_LPAREN)

  fun getCondition(): GrExpression? = skipWhitespacesAndComments(getLParenth()?.nextSibling, true) as? GrExpression

  fun getRParenth(): PsiElement? = findChildByType(T_RPAREN)

  override fun <T : GrCondition> replaceBody(newBody: T): T = replaceBody(newBody, body, node, project)
}
