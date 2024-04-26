// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions

import com.intellij.lang.ASTNode
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement

class RemoveParenthesesFromMethodCallIntention : GrPsiUpdateIntention() {

  override fun getElementPredicate(): PsiElementPredicate = Predicate

  override fun isStopElement(element: PsiElement): Boolean = super.isStopElement(element) || element is GrStatementOwner

  override fun processIntention(element: PsiElement, context: ActionContext, updater: ModPsiUpdater) {
    val newText = removeParentheses(element as GrMethodCallExpression)
    val statement = GroovyPsiElementFactory.getInstance(context.project).createStatementFromText(newText)
    element.replace(statement)
  }

  companion object {
    private fun removeParentheses(call: GrMethodCallExpression): String {
      val copy = call.copy() as GrMethodCallExpression
      copy.argumentList.apply {
        leftParen?.node?.replaceWithSpace()
        rightParen?.node?.replaceWithSpace()
      }
      return copy.text
    }

    private fun ASTNode.replaceWithSpace() {
      val parent = treeParent
      parent.addLeaf(TokenType.WHITE_SPACE, " ", this)
      parent.removeChild(this)
    }
  }

  private object Predicate : PsiElementPredicate {

    override fun satisfiedBy(element: PsiElement): Boolean {
      val call = element as? GrMethodCallExpression ?: return false
      return checkArguments(call) && checkPosition(call) && checkParseableWithoutParentheses(call)
    }

    private fun checkArguments(call: GrMethodCallExpression): Boolean {
      val argumentList = call.argumentList
      val arguments = argumentList.allArguments
      if (call.hasClosureArguments()) {
        return arguments.isEmpty() // foo() {} -> foo {}
      }
      if (arguments.isEmpty()) {
        // foo ()
        return false
      }
      if (arguments.first() is GrClosableBlock) {
        // foo({}) -> foo {}, but not foo({}, a, b, c)
        return arguments.size == 1
      }
      if (arguments.first() is GrListOrMap) {
        // foo([])
        // foo([1])
        // foo([a: 1])
        return false
      }
      return true
    }

    private fun checkPosition(call: GrMethodCallExpression): Boolean {
      if (isExpressionStatement(call)) return true // top level
      val parent = call.parent
      if (parent is GrVariable && parent.initializerGroovy === call) {
        // def a = foo(42) -> def a = foo 42
        return true
      }
      if (parent is GrAssignmentExpression && parent.rValue === call) {
        // a = foo(42) -> a = foo 42
        return true
      }
      return false
    }

    private fun checkParseableWithoutParentheses(call: GrMethodCallExpression): Boolean {
      val newText = removeParentheses(call)
      try {
        GroovyPsiElementFactory.getInstance(call.project).createStatementFromText(newText)
      }
      catch (e: IncorrectOperationException) {
        return false
      }
      return true
    }
  }
}
