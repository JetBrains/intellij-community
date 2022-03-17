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
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.completion.GinqCompletionUtils
import org.jetbrains.plugins.groovy.ext.ginq.formatting.GINQ_AWARE_GROOVY_BLOCK_PRODUCER
import org.jetbrains.plugins.groovy.ext.ginq.formatting.produceGinqFormattingBlock
import org.jetbrains.plugins.groovy.ext.ginq.resolve.GinqResolveUtils
import org.jetbrains.plugins.groovy.ext.ginq.types.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.ext.ginq.types.inferGeneralGinqType
import org.jetbrains.plugins.groovy.ext.ginq.types.inferLocalReferenceExpressionType
import org.jetbrains.plugins.groovy.ext.ginq.types.inferOverType
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
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
    val errors = getParsedGinqErrors(macroCall).mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(it.first)
        .descriptionAndTooltip(it.second)
        .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).create()
    }
    val keywords = mutableListOf<PsiElement>()
    val softKeywords = mutableListOf<PsiElement>()
    val warnings = mutableListOf<Pair<PsiElement, @Nls String>>()
    macroCall.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitElement(element: GroovyPsiElement) {
        val nestedGinq = element.getStoredGinq()
        if (nestedGinq != null) {
          keywords.addAll(nestedGinq.getQueryFragments().map { it.keyword })
          keywords.addAll(nestedGinq.select.projections.flatMap { projection ->
            projection.windows.flatMap { listOfNotNull(it.overKw, it.rowsOrRangeKw, it.partitionKw, it.orderBy?.keyword) }
          })
          warnings.addAll(getTypecheckingWarnings(nestedGinq))
          softKeywords.addAll((nestedGinq.orderBy?.sortingFields?.mapNotNull { it.orderKw } ?: emptyList()) +
                              (nestedGinq.orderBy?.sortingFields?.mapNotNull { it.nullsKw } ?: emptyList()) +
                              (nestedGinq.select.projections.flatMap { projection ->
                                projection.windows.flatMap { window ->
                                  window.orderBy?.sortingFields?.flatMap {
                                    listOfNotNull(it.orderKw, it.nullsKw)
                                  } ?: emptyList()
                                }
                              }))
        }
        super.visitElement(element)
      }
    })
    return keywords.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + softKeywords.mapNotNull {
      val key = if (it.parent is GrMethodCall) GroovySyntaxHighlighter.STATIC_METHOD_ACCESS else GroovySyntaxHighlighter.STATIC_FIELD
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(key).create()
    } + warnings.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(it.first)
        .severity(HighlightSeverity.WARNING).textAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).descriptionAndTooltip(it.second).create()
    } + errors
  }

  private fun getTypecheckingWarnings(ginq: GinqExpression): Collection<Pair<PsiElement, @Nls String>> {
    val dataSourceFragments = ginq.getDataSourceFragments()
    val filteringFragments = ginq.getFilterFragments()
    val filterResults = filteringFragments.mapNotNull { fragment ->
      val type = fragment.filter.type
      val parentCall = fragment.filter.parentOfType<GrMethodCall>()?.parentOfType<GrMethodCall>()?.invokedExpression?.castSafelyTo<GrReferenceExpression>()?.takeIf { it.referenceName == "exists" }
      if (type != PsiType.BOOLEAN && type?.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) != true && parentCall == null) {
        fragment.filter to GroovyBundle.message("ginq.error.message.boolean.condition.expected")
      }
      else {
        null
      }
    }
    val dataSourceResults = dataSourceFragments.mapNotNull {
      val type = inferDataSourceComponentType(it.dataSource.type)
      if (type == null) {
        it.dataSource to GroovyBundle.message("ginq.error.message.container.expected")
      }
      else {
        null
      }
    }
    return filterResults + dataSourceResults
  }

  override fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? {
    val ginq = if (expression == macroCall) getParsedGinqTree(macroCall) else expression.getStoredGinq()
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
    val tree = getParsedGinqTree(macroCall) ?: return false
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
    val topTree = getParsedGinqTree(macroCall) ?: return true
    val tree = place.ginqParents(macroCall, topTree).first()
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

  override fun computeCompletionVariants(macroCall: GrMethodCall, parameters: CompletionParameters, result: CompletionResultSet) = with(
    GinqCompletionUtils) {
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
    val topTree = getParsedGinqTree(macroCall) ?: return super.computeFormattingBlock(macroCall, node, context)
    val newContext = FormattingContext(context.settings, context.alignmentProvider, context.groovySettings, context.isForbidWrapping,
                                       context.isForbidNewLineInSpacing, GINQ_AWARE_GROOVY_BLOCK_PRODUCER)
    return produceGinqFormattingBlock(topTree, newContext, node)
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    val tree = getParsedGinqTree(macroCall) ?: return null
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