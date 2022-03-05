// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.types.GrNamedRecordType
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
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
      CachedValueProvider.Result(doGetParsedGinqTree(macroCall), PsiModificationTracker.MODIFICATION_COUNT)
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
    keywords.addAllIfNotNull(from.fromKw, where?.whereKw, groupBy?.groupByKw, groupBy?.having?.havingKw, orderBy?.orderByKw, limit?.limitKw,
                             select.selectKw)
    keywords.addAll(join.mapNotNull { it.onCondition?.onKw })
    keywords.addAll(join.map { it.joinKw })
    val softKeywords = mutableListOf<PsiElement?>()
    softKeywords.addAll(orderBy?.sortingFields?.mapNotNull { it.orderKw } ?: emptyList())
    macroCall.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitElement(element: GroovyPsiElement) {
        val nestedGinq = element.getUserData(injectedGinq)
        if (nestedGinq != null) {
          keywords.addAllIfNotNull(nestedGinq.from.fromKw, nestedGinq.where?.whereKw, nestedGinq.groupBy?.groupByKw, nestedGinq.groupBy?.having?.havingKw, nestedGinq.orderBy?.orderByKw, nestedGinq.limit?.limitKw, nestedGinq.select.selectKw)
          keywords.addAll(nestedGinq.joins.mapNotNull { it.onCondition?.onKw })
          keywords.addAll(nestedGinq.joins.map { it.joinKw })
          softKeywords.addAll(nestedGinq.orderBy?.sortingFields?.mapNotNull { it.orderKw } ?: emptyList())
        } else {
          super.visitElement(element)
        }
      }
    })
    return keywords.filterNotNull().mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + softKeywords.filterNotNull().mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.STATIC_FIELD).create()
    }
  }

  override fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? {
    if (expression == macroCall) {
      if (macroCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName == "GQL") {
        val ginq = getParsedGinqTree(macroCall) ?: return null
        val namedRecord = GrNamedRecordType(ginq)
        val facade = JavaPsiFacade.getInstance(macroCall.project)
        return facade.findClass(CommonClassNames.JAVA_UTIL_LIST, macroCall.resolveScope)?.let { facade.elementFactory.createType(it, namedRecord) }
      }
    }
    if (expression is GrReferenceExpression) {
      val tree = getParsedGinqTree(macroCall) ?: return null
      val resolved = expression.staticReference.resolve()
      val dataSourceFragment = tree.getDataSourceFragments().find { it.alias == resolved }
      if (dataSourceFragment != null) {
        return dataSourceFragment.dataSource.type?.let(::inferDataSourceComponentType)
      }
    }
    return null
  }

  override fun isUntransformed(macroCall: GrMethodCall, element: GroovyPsiElement): Boolean {
    getParsedGinqTree(macroCall) ?: return false
    return element.parents(true).any { it.getUserData(UNTRANSFORMED_ELEMENT) != null }
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
    val tree = getParsedGinqTree(macroCall) ?: return null
    val referenceName = element.castSafelyTo<GrReferenceElement<*>>()?.referenceName ?: return null
    val hierarchy = element.ginqParents(macroCall, tree)
    for (ginq in hierarchy) {
      val bindings  = ginq.joins.map { it.alias } + listOf(ginq.from.alias) + (ginq.groupBy?.classifiers?.mapNotNull(AliasedExpression::alias) ?: emptyList())
      val binding = bindings.find { it.text == referenceName }
      if (binding != null) {
        return ElementResolveResult(binding)
      }
    }
    return null
  }

  companion object {
    @JvmStatic
    val UNTRANSFORMED_ELEMENT: Key<Unit> = Key.create("Untransformed psi element within Groovy macro")
  }
}