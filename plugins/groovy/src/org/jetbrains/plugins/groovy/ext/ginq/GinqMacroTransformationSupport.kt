// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.formatting.Block
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.completion.GinqCompletionUtils
import org.jetbrains.plugins.groovy.ext.ginq.formatting.GINQ_AWARE_GROOVY_BLOCK_PRODUCER
import org.jetbrains.plugins.groovy.ext.ginq.formatting.produceGinqFormattingBlock
import org.jetbrains.plugins.groovy.ext.ginq.highlighting.GinqHighlightingVisitor
import org.jetbrains.plugins.groovy.ext.ginq.resolve.GinqResolveUtils
import org.jetbrains.plugins.groovy.ext.ginq.types.inferGeneralGinqType
import org.jetbrains.plugins.groovy.ext.ginq.types.inferLocalReferenceExpressionType
import org.jetbrains.plugins.groovy.ext.ginq.types.inferOverType
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupportEx

internal class GinqMacroTransformationSupport : GroovyMacroTransformationSupportEx {

  override fun isApplicable(macro: PsiMethod): Boolean {
    return macro.name in GINQ_METHODS && macro.containingClass?.name == "GinqGroovyMethods"
  }

  override fun computeHighlighting(macroCall: GrCall): List<HighlightInfo> {
    val errors = getTopParsedGinqErrors(macroCall).mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(it.first)
        .descriptionAndTooltip(it.second)
        .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).create()
    }
    val visitor = GinqHighlightingVisitor()
    macroCall.accept(visitor)
    return visitor.keywords.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + visitor.softKeywords.mapNotNull {
      val key = if (it.parent is GrMethodCall) GroovySyntaxHighlighter.STATIC_METHOD_ACCESS else GroovySyntaxHighlighter.STATIC_FIELD
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(key).create()
    } + visitor.warnings.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(it.first)
        .severity(HighlightSeverity.WARNING).textAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).descriptionAndTooltip(it.second).create()
    } + errors
  }

  override fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? {
    val ginq = if (expression == macroCall) getTopParsedGinqTree(macroCall) else expression.getStoredGinq()
    if (ginq != null) {
      return inferGeneralGinqType(macroCall, ginq, expression, expression == macroCall)
    }
    if (expression is GrMethodCall && expression.resolveMethod().castSafelyTo<OriginInfoAwareElement>()?.originInfo == OVER_ORIGIN_INFO) {
      return inferOverType(expression)
    }
    if (expression is GrReferenceExpression) {
      return inferLocalReferenceExpressionType(macroCall, expression)
    }
    return null
  }

  override fun isUntransformed(macroCall: GrMethodCall, element: PsiElement): Boolean {
    val tree = element.getClosestGinqTree(macroCall) ?: return false
    val localRoots = tree.select.projections.flatMapTo(HashSet()) { projection -> projection.windows.map { it.overKw.parent.parent } }
    for (parent in element.parents(true)) {
      if (parent.isGinqRoot() || localRoots.contains(parent)) {
        return false
      }
      if (parent.isGinqUntransformed()) {
        return true
      }
    }
    return false
  }

  override fun processResolve(macroCall: GrMethodCall,
                              processor: PsiScopeProcessor,
                              state: ResolveState,
                              place: PsiElement): Boolean = with(GinqResolveUtils) {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val tree = place.getClosestGinqTree(macroCall) ?: return true
    if (processor.shouldProcessMethods()) {
      val syntheticFunction = resolveToAggregateFunction(place, name)
                              ?: resolveInOverClause(place, name)
                              ?: resolveToExists(place)
                              ?: resolveToDistinct(place, name, tree)
      if (syntheticFunction != null) {
        return processor.execute(syntheticFunction, state)
      }
    }
    if (processor.shouldProcessProperties() || processor.shouldProcessFields()) {
      resolveSyntheticVariable(place, name, tree)?.let { return processor.execute(it, state) }
    }
    return true
  }

  override fun computeCompletionVariants(macroCall: GrMethodCall, parameters: CompletionParameters, result: CompletionResultSet)
  = with(GinqCompletionUtils) {
    val position = parameters.position
    val tree = position.getClosestGinqTree(macroCall)
    if (tree == null) {
      result.addFromAndSelect(macroCall)
      return@with
    }
    val offset = parameters.offset
    if (isUntransformed(macroCall, position)) {
      result.addGeneralGroovyResults(position, offset, tree, macroCall)
      return
    }
    else {
      result.stopHere()
    }
    result.addGinqKeywords(tree, offset, macroCall, position)
    result.addOrderbyDependentKeywords(position)
    result.addOverKeywords(tree, position)
  }

  override fun computeFormattingBlock(macroCall: GrMethodCall, node: ASTNode, context: FormattingContext): Block {
    if (node !is GrClosableBlock) {
      return super.computeFormattingBlock(macroCall, node, context)
    }
    val topTree = getTopParsedGinqTree(macroCall) ?: return super.computeFormattingBlock(macroCall, node, context)
    val newContext = FormattingContext(context.settings, context.alignmentProvider, context.groovySettings, context.isForbidWrapping,
                                       context.isForbidNewLineInSpacing, GINQ_AWARE_GROOVY_BLOCK_PRODUCER)
    return produceGinqFormattingBlock(topTree, newContext, node)
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    val tree = getTopParsedGinqTree(macroCall) ?: return null
    val referenceName = element.castSafelyTo<GrReferenceElement<*>>()?.referenceName ?: return null
    val hierarchy = element.ginqParents(macroCall, tree)
    for (ginq in hierarchy) {
      val bindings =
        ginq.getDataSourceFragments().map { it.alias } + (ginq.groupBy?.classifiers?.mapNotNull(AliasedExpression::alias) ?: emptyList())
      val binding = bindings.find { it.text == referenceName }
      if (binding != null) {
        return ElementResolveResult(binding)
      }
    }
    return null
  }

}