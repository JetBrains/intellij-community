// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.annotator.intentions.ConvertLambdaToClosureAction
import org.jetbrains.plugins.groovy.annotator.intentions.ReplaceDotFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.*
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

internal class GroovyAnnotatorPre30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitModifierList(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(PsiModifier.DEFAULT) ?: return
    holder.createErrorAnnotation(modifier, GroovyBundle.message("default.modifier.in.old.versions"))
  }

  override fun visitDoWhileStatement(statement: GrDoWhileStatement) {
    super.visitDoWhileStatement(statement)
    holder.createErrorAnnotation(statement.doKeyword, message("unsupported.do.while.statement"))
  }

  override fun visitVariableDeclaration(variableDeclaration: GrVariableDeclaration) {
    super.visitVariableDeclaration(variableDeclaration)
    if (variableDeclaration.parent is GrTraditionalForClause) {
      if (variableDeclaration.isTuple) {
        holder.createErrorAnnotation(variableDeclaration, message("unsupported.tuple.declaration.in.for"))
      }
      else if (variableDeclaration.variables.size > 1) {
        holder.createErrorAnnotation(variableDeclaration, message("unsupported.multiple.variables.in.for"))
      }
    }
  }

  override fun visitExpressionList(expressionList: GrExpressionList) {
    super.visitExpressionList(expressionList)
    if (expressionList.expressions.size > 1) {
      holder.createErrorAnnotation(expressionList, message("unsupported.expression.list.in.for.update"))
    }
  }

  override fun visitTryResourceList(resourceList: GrTryResourceList) {
    super.visitTryResourceList(resourceList)
    holder.createErrorAnnotation(resourceList.firstChild, message("unsupported.resource.list"))
  }

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    super.visitBinaryExpression(expression)
    val operator = expression.operationToken
    val tokenType = operator.node.elementType
    if (tokenType === T_ID || tokenType === T_NID) {
      holder.createErrorAnnotation(operator, message("operator.is.not.supported.in", tokenType))
    }
  }

  override fun visitInExpression(expression: GrInExpression) {
    super.visitInExpression(expression)
    val negation = expression.negationToken
    if (negation != null) {
      holder.createErrorAnnotation(negation, message("unsupported.negated.in"))
    }
  }

  override fun visitInstanceofExpression(expression: GrInstanceOfExpression) {
    super.visitInstanceofExpression(expression)
    val negation = expression.negationToken
    if (negation != null) {
      holder.createErrorAnnotation(negation, message("unsupported.negated.instanceof"))
    }
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    super.visitAssignmentExpression(expression)
    val operator = expression.operationToken
    if (operator.node.elementType === T_ELVIS_ASSIGN) {
      holder.createErrorAnnotation(operator, message("unsupported.elvis.assignment"))
    }
  }

  override fun visitIndexProperty(expression: GrIndexProperty) {
    super.visitIndexProperty(expression)
    val safeAccessToken = expression.safeAccessToken
    if (safeAccessToken != null) {
      holder.createErrorAnnotation(safeAccessToken, message("unsupported.safe.index.access"))
    }
  }

  override fun visitReferenceExpression(expression: GrReferenceExpression) {
    super.visitReferenceExpression(expression)
    val dot = expression.dotToken ?: return
    val tokenType = dot.node.elementType
    if (tokenType === T_METHOD_REFERENCE) {
      holder.createErrorAnnotation(dot, message("operator.is.not.supported.in", tokenType)).apply {
        val descriptor = createDescriptor(dot)
        val fix = ReplaceDotFix(tokenType, T_METHOD_CLOSURE)
        registerFix(fix, descriptor)
      }
    }
  }

  override fun visitArrayInitializer(arrayInitializer: GrArrayInitializer) {
    super.visitArrayInitializer(arrayInitializer)
    holder.createErrorAnnotation(arrayInitializer, message("unsupported.array.initializers"))
  }

  override fun visitLambdaExpression(expression: GrLambdaExpression) {
    super.visitLambdaExpression(expression)
    holder.createErrorAnnotation(expression.arrow, message("unsupported.lambda")).apply {
      registerFix(ConvertLambdaToClosureAction(expression))
    }
  }

  override fun visitTypeDefinitionBody(typeDefinitionBody: GrTypeDefinitionBody) {
    super.visitTypeDefinitionBody(typeDefinitionBody)
    checkAmbiguousCodeBlockInDefinition(typeDefinitionBody)
  }

  private fun checkAmbiguousCodeBlockInDefinition(typeDefinitionBody: GrTypeDefinitionBody) {
    val parent = typeDefinitionBody.parent as? GrAnonymousClassDefinition ?: return

    val prev = typeDefinitionBody.prevSibling
    if (!PsiUtil.isLineFeed(prev)) return

    val newExpression = parent.parent as? GrNewExpression ?: return

    val statementOwner = PsiTreeUtil.getParentOfType(newExpression, GrStatementOwner::class.java)

    val parenthesizedExpression = PsiTreeUtil.getParentOfType(newExpression, GrParenthesizedExpression::class.java)
    if (parenthesizedExpression != null && PsiTreeUtil.isAncestor(statementOwner, parenthesizedExpression, true)) return

    val argumentList = PsiTreeUtil.getParentOfType(newExpression, GrArgumentList::class.java)
    if (argumentList != null && argumentList !is GrCommandArgumentList) {
      if (PsiTreeUtil.isAncestor(statementOwner, argumentList, true)) return
    }

    holder.createErrorAnnotation(typeDefinitionBody, message("ambiguous.code.block"))
  }

  override fun visitBlockStatement(blockStatement: GrBlockStatement) {
    super.visitBlockStatement(blockStatement)
    if (blockStatement.parent is GrStatementOwner) {
      holder.createErrorAnnotation(blockStatement, message("ambiguous.code.block"))
    }
  }

  override fun visitClosure(closure: GrClosableBlock) {
    super.visitClosure(closure)
    if (!closure.hasParametersSection() && !followsError(closure) && isClosureAmbiguous(closure)) {
      holder.createErrorAnnotation(closure, GroovyBundle.message("ambiguous.code.block"))
    }
  }

  /**
   * for example if (!(a inst)) {}
   * ^
   * we are here
   */
  private fun followsError(closure: GrClosableBlock): Boolean {
    val prev = closure.prevSibling
    return prev is PsiErrorElement || prev is PsiWhiteSpace && prev.getPrevSibling() is PsiErrorElement
  }

  private fun isClosureAmbiguous(closure: GrClosableBlock): Boolean {
    if (mayBeAnonymousBody(closure)) return true
    var place: PsiElement = closure
    while (true) {
      val parent = place.parent
      if (parent == null || parent is GrUnAmbiguousClosureContainer) return false

      if (PsiUtil.isExpressionStatement(place)) return true
      if (parent.firstChild !== place) return false
      place = parent
    }
  }

  private fun mayBeAnonymousBody(closure: GrClosableBlock): Boolean {
    val parent = closure.parent as? GrMethodCallExpression ?: return false
    if (parent.invokedExpression !is GrNewExpression) {
      return false
    }
    if (!ArrayUtil.contains(closure, *parent.closureArguments)) {
      return false
    }
    var run: PsiElement? = parent.parent
    while (run != null) {
      if (run is GrParenthesizedExpression) return false
      if (run is GrReturnStatement || run is GrAssertStatement || run is GrThrowStatement) return true
      run = run.parent
    }
    return false
  }

  override fun visitTypeElement(typeElement: GrTypeElement) {
    typeElement.annotations.forEach {
      holder.createErrorAnnotation(it, message("unsupported.type.annotations"))
    }
  }

  override fun visitCodeReferenceElement(refElement: GrCodeReferenceElement) {
    refElement.annotations.forEach {
      holder.createErrorAnnotation(it, message("unsupported.type.annotations"))
    }
  }
}
