// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GinqUtils")

package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.castSafelyTo
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

fun inferDataSourceComponentType(type: PsiType): PsiType? = when (type) {
  is PsiArrayType -> type.componentType
  is PsiClassType -> {
    extractComponent(type, JAVA_LANG_ITERABLE)
    ?: extractComponent(type, JAVA_UTIL_STREAM_STREAM)
    ?: extractComponent(type, ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE)
  }
  else -> null
}

private fun extractComponent(type : PsiType, className: String) : PsiType? {
  if (InheritanceUtil.isInheritor(type, className)) {
    return PsiUtil.substituteTypeParameter(type, className, 0, false)
  } else {
    return null
  }
}

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Queryable"

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