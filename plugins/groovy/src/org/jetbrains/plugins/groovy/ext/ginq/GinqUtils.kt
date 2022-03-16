// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GinqUtils")

package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

const val GROOVY_GINQ_TRANSFORM_GQ = "groovy.ginq.transform.GQ"

val ginqMethods: Set<String> = setOf(
  "GQ",
  "GQL",
)

val joins : Set<String> = setOf(
  "join",
  "innerjoin",
  "innerhashjoin",
  "leftjoin",
  "lefthashjoin",
  "crossjoin",
  "rightjoin",
  "righthashjoin",
  "fulljoin",
  "fullhashjoin",
)

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Queryable"

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_WINDOW : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Window"

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD: String =
  "org.apache.groovy.ginq.provider.collection.runtime.NamedRecord"

// name to argument and result type
private val aggregateFunctions: Map<String, Pair<String, String>> = mapOf(
  "count" to (JAVA_LANG_OBJECT to JAVA_LANG_LONG),
  "min" to (JAVA_LANG_COMPARABLE to "same as argument"),
  "max" to (JAVA_LANG_COMPARABLE to "same as argument"),
  "sum" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "avg" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "median" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "stdev" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "stdevp" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "var" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "varp" to (JAVA_LANG_NUMBER to JAVA_MATH_BIG_DECIMAL),
  "agg" to (JAVA_LANG_OBJECT to JAVA_LANG_OBJECT)
  )

fun resolveToAggregateFunction(place: PsiElement, name: String): PsiMethod? {
  if (name !in aggregateFunctions.keys) {
    return null
  }
  val call = place.parent?.castSafelyTo<GrMethodCallExpression>() ?: return null
  val args = call.argumentList.allArguments
  val facade = JavaPsiFacade.getInstance(place.project)
  val prototype = facade
                    .findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE,
                               place.resolveScope)
                    ?.findMethodsByName(name)?.find {it.parameters.size == args.size }?.castSafelyTo<PsiMethod>() ?: return null
  val proxy = GrLightMethodBuilder(place.manager, name)
  proxy.navigationElement = prototype
  if (args.isEmpty() && name == "count") {
    proxy.setReturnType(JAVA_LANG_LONG, place.resolveScope)
    return proxy
  }
  val argType = aggregateFunctions.getValue(name).first
  val returnType = aggregateFunctions.getValue(name).second
  val actualArgType: PsiType
  val actualReturnType: PsiType
  if (argType == JAVA_LANG_COMPARABLE || name == "agg") {
    val typeParam = proxy.addTypeParameter("T")
    if (name != "agg") {
      typeParam.extendsList.addReference(JAVA_LANG_COMPARABLE)
    }
    actualArgType = typeParam.type()
    actualReturnType = actualArgType
  } else {
    actualArgType = PsiType.getTypeByName(argType, place.project, place.resolveScope)
    actualReturnType = PsiType.getTypeByName(returnType, place.project, place.resolveScope)
  }
  proxy.returnType = actualReturnType
  proxy.addParameter(GrLightParameter("arg", actualArgType, place))
  return proxy
}

internal const val OVER_ORIGIN_INFO = "Ginq over"

fun resolveInOverClause(place: PsiElement, name: String): PsiMethod? {
  val facade = JavaPsiFacade.getInstance(place.project)
  val over = place.parentsOfType<GrMethodCallExpression>().firstOrNull { call ->
    call.invokedExpression
      .castSafelyTo<GrReferenceExpression>()
      ?.takeIf { it.referenceName == "over"  && it.qualifierExpression != null } != null
  } ?: return null
  val qualifier = (over.invokedExpression as GrReferenceExpression).qualifierExpression!!
  if (place == over.invokedExpression) {
    val method = GrLightMethodBuilder(place.manager, "over")
    method.originInfo = OVER_ORIGIN_INFO
    if (over.argumentList.allArguments.isNotEmpty()) {
      method.addParameter(GrLightParameter("pagination", null, place))
    }
    method.navigationElement = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope)?.findMethodsByName("over", false)?.singleOrNull() ?: method
    return method
  } else if (PsiTreeUtil.isAncestor(qualifier, place, false)) {
    return resolveInOverQualifier(place, name)
  } else {
    return null
  }
}

data class Signature(val returnType: String, val parameters: List<Pair<String, String>>)

val windowFunctions : Map<String, Signature> = mapOf(
  "rowNumber" to Signature(JAVA_LANG_LONG, emptyList()),
  "rank" to Signature(JAVA_LANG_LONG, emptyList()),
  "denseRank" to Signature(JAVA_LANG_LONG, emptyList()),
  "percentRank" to Signature(JAVA_MATH_BIG_DECIMAL, emptyList()),
  "cumeDist" to Signature(JAVA_MATH_BIG_DECIMAL, emptyList()),
  "ntile" to Signature(JAVA_LANG_LONG, listOf("expr" to JAVA_LANG_LONG)),
  "lead" to Signature("T", listOf("expr" to "T", "?offset" to JAVA_LANG_LONG, "?default" to "T")),
  "lag" to Signature("T", listOf("expr" to "T", "?offset" to JAVA_LANG_LONG, "?default" to "T")),
  "firstValue" to Signature("T", listOf("expr" to "T")),
  "lastValue" to Signature("T", listOf("expr" to "T")),
  "nthValue" to Signature("T", listOf("expr" to "T", "n" to JAVA_LANG_LONG)),
  )

fun resolveInOverQualifier(place: PsiElement, name: String): PsiMethod? {
  val facade = JavaPsiFacade.getInstance(place.project)
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
  proxy.navigationElement = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_WINDOW, place.resolveScope)?.findMethodsByName(name, false)?.find { it.parameterList.parametersCount == parameters.size } ?: proxy
  return proxy
}
