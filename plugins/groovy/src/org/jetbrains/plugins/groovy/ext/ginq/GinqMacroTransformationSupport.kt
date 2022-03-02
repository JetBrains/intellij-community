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
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
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
    return CachedValuesManager.getCachedValue(macroCall, rootGinq, CachedValueProvider {
      CachedValueProvider.Result(doGetParsedGinqTree(macroCall), macroCall)
    })
  }

  private fun doGetParsedGinqTree(macroCall: GrCall): GinqExpression? {
    val closure = macroCall.expressionArguments.filterIsInstance<GrClosableBlock>().singleOrNull()
                  ?: macroCall.closureArguments.singleOrNull()
                  ?: return null
    return parseGinq(closure)
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
    val keywords = mutableListOf<PsiElement?>()
    keywords.addAllIfNotNull(from.fromKw, where?.whereKw, groupBy?.groupByKw, orderBy?.orderByKw, limit?.limitKw,
                             select.selectKw)
    keywords.addAll(join.mapNotNull { it.onCondition?.onKw })
    keywords.addAll(join.map { it.joinKw })
    macroCall.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitElement(element: GroovyPsiElement) {
        val nestedGinq = element.getUserData(injectedGinq)
        if (nestedGinq != null) {
          keywords.addAllIfNotNull(nestedGinq.from.fromKw, nestedGinq.where?.whereKw, nestedGinq.groupBy?.groupByKw, nestedGinq.orderBy?.orderByKw, nestedGinq.limit?.limitKw, nestedGinq.select.selectKw)
          keywords.addAll(nestedGinq.join.mapNotNull { it.onCondition?.onKw })
          keywords.addAll(nestedGinq.join.map { it.joinKw })
        } else {
          super.visitElement(element)
        }
      }
    })
    return keywords.filterNotNull().mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    }
  }

  override fun processResolve(scope: PsiElement, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val tree = scope.parent?.parent?.castSafelyTo<GrCall>()?.let(this::getParsedGinqTree) ?: return true
    val fromBinding = tree.from.alias
    if (name == fromBinding.referenceName) {
      return processor.execute(fromBinding, state)
    }
    return true
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    if (element !is GrReferenceExpression) {
      return null
    }
    val hierarchy = element.ginqParents()
    for (ginq in hierarchy) {
      val bindings  = ginq.join.map { it.alias } + listOf(ginq.from.alias)
      val binding = bindings.find { it.referenceName == element.referenceName }
      if (binding != null) {
        return ElementResolveResult(binding)
      }
    }
    return null
  }
}