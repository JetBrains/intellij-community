// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parents
import com.intellij.util.asSafely
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
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformerEx

class GinqTransformationPerformer(private val root: GinqRootPsiElement) : GroovyInlineASTTransformationPerformerEx {

  override fun computeHighlighting(): List<HighlightInfo> {
    val shutdown = getTopShutdownGinq(root)
    if (shutdown != null) {
      return listOfNotNull(shutdown.shutdownKw, shutdown.optionKw)
        .mapNotNull { HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(getSoftKeywordHighlighting(it)).create() }
    }
    val errors = getTopParsedGinqErrors(root).mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(it.first)
        .descriptionAndTooltip(it.second)
        .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).create()
    }
    val visitor = GinqHighlightingVisitor()
    root.psi.accept(visitor)
    return visitor.keywords.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + visitor.softKeywords.mapNotNull {
      val key = getSoftKeywordHighlighting(it)
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(key).create()
    } + visitor.warnings.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(it.first)
        .severity(HighlightSeverity.WARNING).textAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).descriptionAndTooltip(it.second).create()
    } + errors
  }

  private fun getSoftKeywordHighlighting(it: PsiElement) =
    if (it.parent is GrMethodCall) GroovySyntaxHighlighter.STATIC_METHOD_ACCESS else GroovySyntaxHighlighter.STATIC_FIELD

  override fun computeType(expression: GrExpression): PsiType? {
    val topGinqExpression = getTopParsedGinqTree(root) ?: return null
    val ginq = if (expression is GrMethodCall && (expression == root.psi || expression.refCallIdentifier() == topGinqExpression.select?.keyword))
      topGinqExpression
    else
      expression.getStoredGinq()
    if (ginq != null) {
      return inferGeneralGinqType(root.asSafely<GinqRootPsiElement.Call>()?.psi, ginq, expression, expression == root.psi)
    }
    if (expression is GrMethodCall && expression.resolveMethod().asSafely<OriginInfoAwareElement>()?.originInfo == OVER_ORIGIN_INFO) {
      return inferOverType(expression)
    }
    if (expression is GrReferenceExpression) {
      return inferLocalReferenceExpressionType(root, expression)
    }
    return null
  }

  override fun isUntransformed(element: PsiElement): Boolean {
    if (getTopShutdownGinq(root) != null) return false
    val tree = element.getClosestGinqTree(root) ?: return false
    val localRoots = tree.select?.projections?.flatMapTo(HashSet()) { projection -> projection.windows.map { it.overKw.parent.parent } }
    for (parent in element.parents(true)) {
      if (parent.isGinqRoot() || localRoots?.contains(parent) == true) {
        return false
      }
      if (parent.isGinqUntransformed()) {
        return true
      }
    }
    return false
  }

  override fun processResolve(processor: PsiScopeProcessor,
                              state: ResolveState,
                              place: PsiElement): Boolean = with(GinqResolveUtils) {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val tree = place.getClosestGinqTree(root) ?: return true
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

  override fun computeCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) = with(GinqCompletionUtils) {
    val position = parameters.position
    val tree = position.getClosestGinqTree(root)
    if (tree == null) {
      result.addFromSelectShutdown(root, position)
      return@with
    }
    val offset = parameters.offset
    if (isUntransformed(position)) {
      result.addGeneralGroovyResults(position, offset, tree, root)
      return
    }
    else {
      result.stopHere()
    }
    result.addGinqKeywords(tree, offset, root, position)
    result.addOrderbyDependentKeywords(position)
    result.addOverKeywords(tree, position)
  }

  override fun computeFormattingBlock(node: ASTNode, context: FormattingContext): Block {
    val children = GroovyBlockGenerator.visibleChildren(node)
    val blocks = when (root) {
      is GinqRootPsiElement.Call -> children.map { if (it is GrClosableBlock) computeParticularFormatingBlock(it.node, context) else GroovyBlock(it, Indent.getNoneIndent(), null, context) }
      is GinqRootPsiElement.Method -> children.map { if (it is GrOpenBlock) computeParticularFormatingBlock(it.node, context) else GroovyBlock(it, Indent.getNoneIndent(), null, context) }
    }
    val indent = if (node.treeParent is FileElement) Indent.getNoneIndent() else Indent.getNormalIndent()
    return SyntheticGroovyBlock(blocks, Wrap.createWrap(WrapType.NORMAL, false), indent, Indent.getNoneIndent(), context)
  }

  private fun computeParticularFormatingBlock(node: ASTNode, context: FormattingContext): Block {
    val topTree = getTopParsedGinqTree(root) ?: return super.computeFormattingBlock(node, context)
    val newContext = FormattingContext(context.settings, context.alignmentProvider, context.groovySettings, context.isForbidWrapping,
                                       context.isForbidNewLineInSpacing, GINQ_AWARE_GROOVY_BLOCK_PRODUCER)
    return produceGinqFormattingBlock(topTree, newContext, node)
  }

  override fun computeStaticReference(element: PsiElement): ElementResolveResult<PsiElement>? {
    val tree = getTopParsedGinqTree(root) ?: return null
    val referenceName = element.asSafely<GrReferenceElement<*>>()?.referenceName ?: return null
    val hierarchy = element.ginqParents(root, tree)
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