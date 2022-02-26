// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqJoinExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.parseGinqBody
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupport

internal class GinqMacroTransformationSupport : GroovyMacroTransformationSupport {
  override fun isApplicable(macroCall: GrMethodCall): Boolean {
    val actualCall = macroCall.invokedExpression
    if (!(actualCall is GrReferenceExpression && actualCall.referenceName in ginqMethods)) return false
    // todo: cache the following call
    return isGinqAvailable(macroCall)
  }

  private fun getParsedGinqTree(macroCall: GrCall): GinqExpression? {
    return CachedValuesManager.getCachedValue(macroCall, CachedValueProvider {
      CachedValueProvider.Result(doGetParsedGinqTree(macroCall), macroCall)
    })
  }

  private fun doGetParsedGinqTree(macroCall: GrCall): GinqExpression? {
    val closure = macroCall.expressionArguments.filterIsInstance<GrClosableBlock>().singleOrNull()
                  ?: macroCall.closureArguments.singleOrNull()
                  ?: return null
    return parseGinqBody(closure)

  }

  override fun computeHighlighing(macroCall: GrCall): List<HighlightInfo> {
    val (from,
      join,
      where,
      groupBy,
      orderBy,
      limit,
      select
    ) = getParsedGinqTree(macroCall) ?: return emptyList()
    val keywords = listOfNotNull(from.fromKw, where?.whereKw, groupBy?.groupByKw, orderBy?.orderByKw, limit?.limitKw,
                                 select.selectKw) + join.map(GinqJoinExpression::joinKw) + join.mapNotNull { it.onCondition?.onKw }
    return keywords.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    }
  }

  override fun processResolve(scope: PsiElement, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val tree = scope.parent?.parent?.castSafelyTo<GrCall>()?.let(this::getParsedGinqTree) ?: return true
    val fromBinding = tree.fromExpression.aliasExpression
    if (name == fromBinding.referenceName) {
      return processor.execute(fromBinding, state)
    }
    return true
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    if (element !is GrReferenceExpression) {
      return null
    }
    val tree = getParsedGinqTree(macroCall) ?: return null
    val bindings  = tree.joinExpressions.map { it.aliasExpression } + listOf(tree.fromExpression.aliasExpression)
    val binding = bindings.find { it.referenceName == element.referenceName }
    return binding?.let(::ElementResolveResult)
  }
}