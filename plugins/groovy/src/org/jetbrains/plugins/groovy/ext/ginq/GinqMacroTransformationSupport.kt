// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.types.GrSyntheticNamedRecordClass
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupport

internal class GinqMacroTransformationSupport : GroovyMacroTransformationSupport {

  override fun isApplicable(macro: PsiMethod): Boolean {
    return macro.name in ginqMethods && macro.containingClass?.name == "GinqGroovyMethods"
  }

  private fun getParsedGinqTree(macroCall: GrCall): GinqExpression? {
    return getParsedGinqInfo(macroCall).second
  }

  private fun getParsedGinqErrors(macroCall: GrCall): List<ParsingError> {
    return getParsedGinqInfo(macroCall).first
  }

  private fun getParsedGinqInfo(macroCall: GrCall): Pair<List<ParsingError>, GinqExpression?> {
    return CachedValuesManager.getCachedValue(macroCall, rootGinq, CachedValueProvider {
      CachedValueProvider.Result(doGetParsedGinqTree(macroCall), PsiModificationTracker.MODIFICATION_COUNT)
    })
  }

  private fun doGetParsedGinqTree(macroCall: GrCall): Pair<List<ParsingError>, GinqExpression?> {
    val closure = macroCall.expressionArguments.filterIsInstance<GrClosableBlock>().singleOrNull()
                  ?: macroCall.closureArguments.singleOrNull()
                  ?: return emptyList<ParsingError>() to null
    return parseGinq(closure)
  }

  override fun computeHighlighing(macroCall: GrCall): List<HighlightInfo> {
    val errors = getParsedGinqErrors(macroCall).mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(it.first)
        .descriptionAndTooltip(it.second)
        .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).create() }
    val keywords = mutableListOf<PsiElement?>()
    val softKeywords = mutableListOf<PsiElement?>()
    macroCall.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitElement(element: GroovyPsiElement) {
        val nestedGinq = element.getStoredGinq()
        if (nestedGinq != null) {
          keywords.addAllIfNotNull(nestedGinq.from.fromKw, nestedGinq.where?.whereKw, nestedGinq.groupBy?.groupByKw,
                                   nestedGinq.groupBy?.having?.havingKw, nestedGinq.orderBy?.orderByKw, nestedGinq.limit?.limitKw,
                                   nestedGinq.select.selectKw)
          keywords.addAll(nestedGinq.joins.mapNotNull { it.onCondition?.onKw })
          keywords.addAll(nestedGinq.joins.map { it.joinKw })
          softKeywords.addAll((nestedGinq.orderBy?.sortingFields?.mapNotNull { it.orderKw } ?: emptyList()) + (nestedGinq.orderBy?.sortingFields?.mapNotNull { it.nullsKw } ?: emptyList()))
        }
        super.visitElement(element)
      }
    })
    return keywords.filterNotNull().mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + softKeywords.filterNotNull().mapNotNull {
      val key = if (it.parent is GrMethodCall) GroovySyntaxHighlighter.STATIC_METHOD_ACCESS else GroovySyntaxHighlighter.STATIC_FIELD
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(key).create()
    } + errors
  }

  override fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? {
    val ginq = if (expression == macroCall) getParsedGinqTree(macroCall) else expression.getStoredGinq()
    val facade = JavaPsiFacade.getInstance(macroCall.project)
    if (ginq != null) {
      val invokedCall = macroCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName
      val container = if (invokedCall == "GQL") CommonClassNames.JAVA_UTIL_LIST else if (invokedCall == "GQ") ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE else null
      if (container != null) {
        val singleProjection = ginq.select.projections.singleOrNull()?.takeIf { it.alias == null }
        val componentType = if (singleProjection != null) {
          singleProjection.aggregatedExpression.type ?: PsiType.NULL
        } else {
          val namedRecord = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, expression.resolveScope)
          namedRecord?.let { GrSyntheticNamedRecordClass(ginq, it).type() } ?: PsiType.NULL
        }
        return facade.findClass(container, macroCall.resolveScope)?.let {
          facade.elementFactory.createType(it, componentType)
        }
      }
    }
    if (expression is GrReferenceExpression) {
      val tree = getParsedGinqTree(macroCall) ?: return null
      if (expression.referenceName == "_g") {
        val custom = resolveToCustomMember(expression, "_g", tree)
        if (custom != null) {
          return custom.type
        }
      }
      val resolved = expression.staticReference.resolve()
      val dataSourceFragment = expression.ginqParents(macroCall, tree).firstNotNullOfOrNull { ginq ->  ginq.getDataSourceFragments().find { it.alias == resolved } }
      if (dataSourceFragment != null) {
        return dataSourceFragment.dataSource.type?.let(::inferDataSourceComponentType)
      }
    }
    return null
  }

  override fun isUntransformed(macroCall: GrMethodCall, element: GroovyPsiElement): Boolean {
    getParsedGinqTree(macroCall) ?: return false
    for (parent in element.parents(true)) {
      if (parent.getUserData(rootGinq) != null) {
        return false
      }
      if (parent.getUserData(UNTRANSFORMED_ELEMENT) != null) {
        return true
      }
    }
    return false
  }

  override fun processResolve(macroCall: GrMethodCall,
                              scope: PsiElement,
                              processor: PsiScopeProcessor,
                              state: ResolveState,
                              place: PsiElement): Boolean {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val tree = getParsedGinqTree(macroCall) ?: return true
    // todo: pick the closest tree
    if (name == "distinct" && processor.shouldProcessMethods() && tree.select.distinct == place) {
      val call = GrLightMethodBuilder(macroCall.manager, "distinct")
      val method = JavaPsiFacade.getInstance(macroCall.project)
        .findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, macroCall.resolveScope)
        ?.findMethodsByName("distinct",false)?.singleOrNull()
      if (method != null) {
        call.navigationElement = method
      }
      val resultTypeCollector = mutableMapOf<String, Lazy<PsiType>>()
      val typeParameters = mutableListOf<PsiTypeParameter>()
      for ((i, arg) in tree.select.projections.withIndex()) {
        val typeParameter = call.addTypeParameter("T$i")
        val typeParameterType = typeParameter.type()
        call.addParameter("expr", typeParameterType)
        if (arg.alias != null) {
          // todo: indexes
          typeParameters.add(typeParameter)
          resultTypeCollector[arg.alias.text] = lazy(LazyThreadSafetyMode.NONE) { typeParameterType }
        }
      }
      val clazz = JavaPsiFacade.getInstance(macroCall.project).findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, macroCall.resolveScope)
      call.returnType = clazz?.let { GrSyntheticNamedRecordClass(typeParameters, resultTypeCollector, it).type() }

      return processor.execute(call, state)
    }
    if (processor.shouldProcessMethods()) {
      val aggregate = resolveToAggregateFunction(place, name)
      if (aggregate != null) {
        return processor.execute(aggregate, state)
      }
    }
    if (processor.shouldProcessProperties() || processor.shouldProcessFields()) {
      val implicitVariable = resolveToCustomMember(place, name, tree)
      if (implicitVariable != null) {
        // todo: pick the closest tree
        return processor.execute(implicitVariable, state)
      }
    }
    return true
  }

  private fun resolveToCustomMember(place: PsiElement, name: String, tree: GinqExpression): GrLightVariable? {
    if (name == "_g") {
      val facade = JavaPsiFacade.getInstance(place.project)
      val containerClass = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope) ?: return null
      val clazz = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, place.resolveScope)
      val dataSourceTypes = tree.getDataSourceFragments().mapNotNull {
        val aliasName = it.alias.referenceName ?: return@mapNotNull null
        aliasName to lazyPub { it.dataSource.type?.let(::inferDataSourceComponentType) ?: PsiType.NULL }
      }.toMap()
      val type = clazz?.let { GrSyntheticNamedRecordClass(emptyList(), dataSourceTypes, it).type() } ?: return null
      val resultType = facade.elementFactory.createType(containerClass, type)
      return GrLightVariable(place.manager, "_g", resultType, containerClass)
    }
    return null
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    val tree = getParsedGinqTree(macroCall) ?: return null
    val referenceName = element.castSafelyTo<GrReferenceElement<*>>()?.referenceName ?: return null
    val hierarchy = element.ginqParents(macroCall, tree)
    for (ginq in hierarchy) {
      val bindings = ginq.joins.map { it.alias } + listOf(ginq.from.alias) + (ginq.groupBy?.classifiers?.mapNotNull(
        AliasedExpression::alias) ?: emptyList())
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