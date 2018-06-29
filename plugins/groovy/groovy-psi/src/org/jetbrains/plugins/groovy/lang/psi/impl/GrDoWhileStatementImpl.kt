// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_DO
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrDoWhileStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespacesAndComments

class GrDoWhileStatementImpl(node: ASTNode) : GrWhileStatementBase(node), GrDoWhileStatement {

  override fun getDoKeyword(): PsiElement = findNotNullChildByType(KW_DO)

  override fun getBody(): GrStatement? = skipWhitespacesAndComments(doKeyword.nextSibling, true) as? GrStatement

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitDoWhileStatement(this)

  override fun toString(): String = "do-while statement"
}
