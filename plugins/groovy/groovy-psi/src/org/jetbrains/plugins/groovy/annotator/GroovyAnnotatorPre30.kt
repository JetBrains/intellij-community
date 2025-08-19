// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.annotator.intentions.ConvertLambdaToClosureIntention
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
import org.jetbrains.plugins.groovy.lang.psi.util.isApplicationExpression
import org.jetbrains.plugins.groovy.lang.psi.util.isNewLine

internal class GroovyAnnotatorPre30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  private fun error(typeArgumentList: PsiElement, @InspectionMessage msg: String) {
    holder.newAnnotation(HighlightSeverity.ERROR, msg).range(typeArgumentList).create()
  }

  override fun visitModifierList(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(PsiModifier.DEFAULT) ?: return
    error(modifier, message("default.modifier.in.old.versions"))
  }

  override fun visitDoWhileStatement(statement: GrDoWhileStatement) {
    super.visitDoWhileStatement(statement)
    error(statement.doKeyword, message("unsupported.do.while.statement"))
  }

  override fun visitVariableDeclaration(variableDeclaration: GrVariableDeclaration) {
    super.visitVariableDeclaration(variableDeclaration)
    checkTupleVariableIsNotAllowed(variableDeclaration,
                                   holder,
                                   message("tuple.declaration.should.end.with.def.modifier"),
                                   setOf(KW_DEF))

    if (variableDeclaration.parent is GrTraditionalForClause) {
      if (variableDeclaration.isTuple) {
        holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.tuple.declaration.in.for")).create()
      }
      else if (variableDeclaration.variables.size > 1) {
        holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.multiple.variables.in.for")).create()
      }
    }
    else if (variableDeclaration.isTuple) {
      val initializer = variableDeclaration.tupleInitializer
      if (initializer != null && initializer.isApplicationExpression()) {
        error(initializer, message("unsupported.tuple.application.initializer"))
      }
    }
  }

  override fun visitExpressionList(expressionList: GrExpressionList) {
    super.visitExpressionList(expressionList)
    if (expressionList.expressions.size > 1) {
      holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.expression.list.in.for.update")).create()
    }
  }

  override fun visitTryResourceList(resourceList: GrTryResourceList) {
    super.visitTryResourceList(resourceList)
    error(resourceList.firstChild, message("unsupported.resource.list"))
  }

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    super.visitBinaryExpression(expression)
    val operator = expression.operationToken
    val tokenType = operator.node.elementType
    if (tokenType === T_ID || tokenType === T_NID) {
      error(operator, message("operator.is.not.supported.in", tokenType))
    }
  }

  override fun visitInExpression(expression: GrInExpression) {
    super.visitInExpression(expression)
    if (GrInExpression.isNegated(expression)) {
      error(expression.operationToken, message("unsupported.negated.in"))
    }
  }

  override fun visitInstanceofExpression(expression: GrInstanceOfExpression) {
    super.visitInstanceofExpression(expression)
    if (GrInstanceOfExpression.isNegated(expression)) {
      error(expression.operationToken, message("unsupported.negated.instanceof"))
    }
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    super.visitAssignmentExpression(expression)
    val operator = expression.operationToken
    if (operator.node.elementType === T_ELVIS_ASSIGN) {
      error(operator, message("unsupported.elvis.assignment"))
    }
  }

  override fun visitIndexProperty(expression: GrIndexProperty) {
    super.visitIndexProperty(expression)
    val safeAccessToken = expression.safeAccessToken
    if (safeAccessToken != null) {
      error(safeAccessToken, message("unsupported.safe.index.access"))
    }
  }

  override fun visitReferenceExpression(expression: GrReferenceExpression) {
    super.visitReferenceExpression(expression)
    highlightIncorrectDot(expression, T_METHOD_REFERENCE, T_METHOD_CLOSURE)
    highlightIncorrectDot(expression, T_SAFE_CHAIN_DOT, T_SAFE_DOT)
  }

  private fun highlightIncorrectDot(expression: GrReferenceExpression, wrongDot: IElementType, correctDot: IElementType) {
    val dot = expression.dotToken ?: return
    val tokenType = dot.node.elementType
    if (tokenType == wrongDot) {
      val fix = ReplaceDotFix(tokenType, correctDot)
      val message = message("operator.is.not.supported.in", tokenType)
      val descriptor = InspectionManager.getInstance(expression.project).createProblemDescriptor(
        dot, dot, message,
        ProblemHighlightType.ERROR, true
      )
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(dot)
        .newLocalQuickFix(fix, descriptor).registerFix()
        .create()
    }
  }

  override fun visitArrayInitializer(arrayInitializer: GrArrayInitializer) {
    super.visitArrayInitializer(arrayInitializer)
    holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.array.initializers")).create()
  }

  override fun visitLambdaExpression(expression: GrLambdaExpression) {
    super.visitLambdaExpression(expression)
    holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.lambda")).range(expression.arrow)
      .withFix(ConvertLambdaToClosureIntention(expression))
      .create()
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

    holder.newAnnotation(HighlightSeverity.ERROR, message("ambiguous.code.block")).create()
  }

  override fun visitBlockStatement(blockStatement: GrBlockStatement) {
    super.visitBlockStatement(blockStatement)
    if (blockStatement.parent is GrStatementOwner) {
      holder.newAnnotation(HighlightSeverity.ERROR, message("ambiguous.code.block")).create()
    }
  }

  override fun visitClosure(closure: GrClosableBlock) {
    super.visitClosure(closure)
    if (!closure.hasParametersSection() && !followsError(closure) && isClosureAmbiguous(closure)) {
      holder.newAnnotation(HighlightSeverity.ERROR, message("ambiguous.code.block")).create()
    }
  }

  override fun visitParenthesizedExpression(expression: GrParenthesizedExpression) {
    super.visitParenthesizedExpression(expression)
    val operand = expression.operand
    if (operand is GrCall && operand.isApplicationExpression()) {
      holder.newAnnotation(HighlightSeverity.ERROR, message("call.without.parentheses.are.supported.since.groovy.3")).range(operand).create()
    }
  }

  override fun visitApplicationStatement(applicationStatement: GrApplicationStatement) {
    super.visitApplicationStatement(applicationStatement)
    val invoked = applicationStatement.invokedExpression
    val badNewline = invoked.firstChild?.nextSibling?.takeIf { it.isNewLine() }
    if (badNewline != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, message("newlines.here.are.available.since.groovy.3")).range(badNewline).create()
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
      if (run is GrReturnStatement || run is GrAssertStatement || run is GrThrowStatement || run is GrCommandArgumentList) return true
      run = run.parent
    }
    return false
  }

  override fun visitTypeElement(typeElement: GrTypeElement) {
    typeElement.annotations.forEach {
      error(it, message("unsupported.type.annotations"))
    }
  }

  override fun visitCodeReferenceElement(refElement: GrCodeReferenceElement) {
    refElement.annotations.forEach {
      error(it, message("unsupported.type.annotations"))
    }
  }

  override fun visitTupleAssignmentExpression(expression: GrTupleAssignmentExpression) {
    val rValue = expression.rValue
    if (rValue != null && rValue.isApplicationExpression()) {
      error(rValue, message("unsupported.tuple.application.initializer"))
    }
  }
}
