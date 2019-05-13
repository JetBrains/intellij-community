/*
 * Copyright 2008 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions.conversions

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement

class RemoveParenthesesFromMethodCallIntention : Intention() {

  override fun getElementPredicate(): PsiElementPredicate = Predicate

  override fun isStopElement(element: PsiElement): Boolean = super.isStopElement(element) || element is GrStatementOwner

  override fun processIntention(element: PsiElement, project: Project, editor: Editor) {
    val newText = removeParentheses(element as GrMethodCallExpression)
    val statement = GroovyPsiElementFactory.getInstance(project).createStatementFromText(newText)
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
