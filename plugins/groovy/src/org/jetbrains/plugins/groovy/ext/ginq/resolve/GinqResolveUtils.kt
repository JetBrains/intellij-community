// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.resolve

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.GINQ_EXISTS
import org.jetbrains.plugins.groovy.ext.ginq.GinqSupport
import org.jetbrains.plugins.groovy.ext.ginq.OVER_ORIGIN_INFO
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.callRefName
import org.jetbrains.plugins.groovy.ext.ginq.types.GrSyntheticNamedRecordClass
import org.jetbrains.plugins.groovy.ext.ginq.types.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.ext.ginq.windowFunctions
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

object GinqResolveUtils : GinqSupport {

  fun resolveSyntheticVariable(place: PsiElement, name: String, tree: GinqExpression): GrLightVariable? {
    if (name != _G && name != _RN) {
      return null
    }
    val containerClass = getQueryable(place) ?: return null
    if (name == _G) {
      val clazz = getNamedRecord(place)
      val dataSourceTypes = tree.getDataSourceFragments().mapNotNull {
        val aliasName = it.alias.referenceName ?: return@mapNotNull null
        aliasName to lazyPub { inferDataSourceComponentType(it.dataSource.type) ?: PsiType.NULL }
      }.toMap()
      val type = clazz?.let { GrSyntheticNamedRecordClass(emptyList(), dataSourceTypes, emptyList(), it).type() } ?: return null
      val resultType = JavaPsiFacade.getInstance(place.project).elementFactory.createType(containerClass, type)
      return GrLightVariable(place.manager, _G, resultType, containerClass)
    } else {
      return GrLightVariable(place.manager, _RN, CommonClassNames.JAVA_LANG_LONG, containerClass)
    }
  }

  fun resolveToExists(place: PsiElement): PsiMethod? {
    if (place !is GrMethodCall || place.callRefName != GINQ_EXISTS) {
      return null
    }
    val method = GrLightMethodBuilder(place.manager, GINQ_EXISTS)
    method.setReturnType(CommonClassNames.JAVA_LANG_BOOLEAN, place.resolveScope)
    findQueryableMethod(place, GINQ_EXISTS)?.let(method::setNavigationElement)
    return method
  }

  fun resolveToAggregateFunction(place: PsiElement, name: String): PsiMethod? {
    if (name !in aggregateFunctions.keys) {
      return null
    }
    val call = place.parent?.asSafely<GrMethodCallExpression>() ?: return null
    val args = call.argumentList.allArguments
    val prototype = getQueryable(place)?.findMethodsByName(name, false)?.find { it.parameters.size == args.size } ?: return null
    val proxy = GrLightMethodBuilder(place.manager, name)
    proxy.navigationElement = prototype
    if (args.isEmpty() && name == "count") {
      return proxy.apply { setReturnType(CommonClassNames.JAVA_LANG_LONG, place.resolveScope) }
    }
    val argType = aggregateFunctions.getValue(name).first
    val returnType = aggregateFunctions.getValue(name).second
    val actualArgType: PsiType
    val actualReturnType: PsiType
    if (argType == CommonClassNames.JAVA_LANG_COMPARABLE || name == "agg") {
      val typeParam = proxy.addTypeParameter("T")
      if (name != "agg") {
        typeParam.extendsList.addReference(CommonClassNames.JAVA_LANG_COMPARABLE)
      }
      actualArgType = typeParam.type()
      actualReturnType = actualArgType
    }
    else {
      actualArgType = PsiType.getTypeByName(argType, place.project, place.resolveScope)
      actualReturnType = PsiType.getTypeByName(returnType, place.project, place.resolveScope)
    }
    proxy.returnType = actualReturnType
    proxy.addParameter(GrLightParameter("arg", actualArgType, place))
    return proxy
  }

  fun resolveInOverClause(place: PsiElement, name: String): PsiMethod? {
    val (qualifier, over) = getOver(place) ?: return null
    if (place == over.invokedExpression) {
      val method = GrLightMethodBuilder(place.manager, OVER)
      method.originInfo = OVER_ORIGIN_INFO
      if (over.argumentList.allArguments.isNotEmpty()) {
        method.addParameter(GrLightParameter("pagination", null, place))
      }
      findQueryableMethod(place, OVER)?.let(method::setNavigationElement)
      return method
    }
    else if (PsiTreeUtil.isAncestor(qualifier, place, false)) {
      return resolveInOverQualifier(place, name)
    }
    else {
      return null
    }
  }

  private fun getOver(place: PsiElement) : Pair<GrExpression, GrMethodCallExpression>? {
    for (callExpression in place.parentsOfType<GrMethodCallExpression>()) {
      val invoked = callExpression.invokedExpression.asSafely<GrReferenceExpression>() ?: continue
      val qualifier = invoked.qualifierExpression
      if (invoked.referenceName == OVER && qualifier != null) {
        return qualifier to callExpression
      }
    }
    return null
  }

  private fun resolveInOverQualifier(place: PsiElement, name: String): PsiMethod? {
    val (returnType, parameters) = windowFunctions[name] ?: return null
    val proxy = GrLightMethodBuilder(place.manager, name)
    val actualReturnType = if (returnType == "T") {
      proxy.addTypeParameter("T").type()
    } else {
      PsiType.getTypeByName(returnType, place.project, place.resolveScope)
    }
    proxy.returnType = actualReturnType
    for ((parameterName, parameterType) in parameters) {
      val actualType = if (parameterType == "T") actualReturnType else PsiType.getTypeByName(parameterType, place.project, place.resolveScope)
      if (parameterName.startsWith('?')) {
        proxy.addParameter(parameterName.substring(1), actualType, true)
      } else {
        proxy.addParameter(parameterName, actualType)
      }
    }
    proxy.navigationElement = getWindow(place)?.findMethodsByName(name, false)?.find { it.parameterList.parametersCount == parameters.size } ?: proxy
    return proxy
  }

  fun resolveToDistinct(place: PsiElement, name: String, tree: GinqExpression): PsiMethod? {
    if (name != DISTINCT || tree.select?.distinct != place) {
      return null
    }
    val call = GrLightMethodBuilder(place.manager, DISTINCT)
    findQueryableMethod(place, DISTINCT)?.let(call::setNavigationElement)
    val resultTypeCollector = LinkedHashMap<String, Lazy<PsiType>>()
    val typeParameters = mutableListOf<PsiTypeParameter>()
    for ((i, arg) in tree.select.projections.withIndex()) {
      val typeParameter = call.addTypeParameter("T$i")
      val typeParameterType = typeParameter.type()
      call.addParameter("expr$i", typeParameterType)
      if (arg.alias != null) {
        typeParameters.add(typeParameter)
        resultTypeCollector[arg.alias.text] = lazy(LazyThreadSafetyMode.NONE) { typeParameterType }
      }
    }
    call.returnType = getNamedRecord(place)?.let {
      GrSyntheticNamedRecordClass(typeParameters, resultTypeCollector, resultTypeCollector.keys.toList(), it).type()
    }
    return call
  }
}

// name to argument and result type
private val aggregateFunctions: Map<String, Pair<String, String>> = mapOf(
  "count" to (CommonClassNames.JAVA_LANG_OBJECT to CommonClassNames.JAVA_LANG_LONG),
  "min" to (CommonClassNames.JAVA_LANG_COMPARABLE to "same as argument"),
  "max" to (CommonClassNames.JAVA_LANG_COMPARABLE to "same as argument"),
  "sum" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "avg" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "median" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "stdev" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "stdevp" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "var" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "varp" to (CommonClassNames.JAVA_LANG_NUMBER to GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL),
  "agg" to (CommonClassNames.JAVA_LANG_OBJECT to CommonClassNames.JAVA_LANG_OBJECT)
)

private const val DISTINCT: String = "distinct"

private const val OVER: String = "over"

@Suppress("ObjectPropertyName")
private const val _G = "_g"

@Suppress("ObjectPropertyName")
private const val _RN: String = "_rn"