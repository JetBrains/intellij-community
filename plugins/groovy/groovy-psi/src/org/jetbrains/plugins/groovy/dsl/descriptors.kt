// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl")

sealed class Descriptor

data class VariableDescriptor(
  val name: String,
  val type: String,
) : Descriptor()

data class MethodDescriptor(
  val isConstructor: Boolean,
  val name: String,
  val namedParameters: List<NamedParameterDescriptor>,
  val parameters: List<VariableDescriptor>, // doesn't include named parameters Map
  val returnType: String,
  val throws: List<String>,
  val containingClass: String?,
  val isStatic: Boolean,
  val bindsTo: PsiElement?,
  val doc: @NlsSafe String?,
  val docUrl: String?,
) : Descriptor()

data class ClosureDescriptor(
  val methodName: String,
  val methodParameterTypes: List<String>,
  val usePlaceContextForTypes: Boolean,
  val isMethodConstructor: Boolean,
  val parameters: List<VariableDescriptor>, // includes named parameters Map
) : Descriptor()

fun parseVariable(args: Map<*, *>): VariableDescriptor {
  return VariableDescriptor(
    name = args["name"].toString(),
    type = stringifyType(args["type"]),
  )
}

fun parseMethod(args: Map<*, *>): MethodDescriptor {
  return MethodDescriptor(
    isConstructor = args["constructor"] == true,
    name = args["name"].toString(),
    namedParameters = namedParams(args),
    parameters = params(args),
    returnType = stringifyType(args["type"]),
    containingClass = args["containingClass"] as? String,
    throws = throws(args),
    isStatic = args["isStatic"] == true,
    bindsTo = args["bindsTo"] as? PsiElement,
    doc = args["doc"] as? String,
    docUrl = args["docUrl"] as? String,
  )
}

private fun namedParams(args: Map<*, *>): List<NamedParameterDescriptor> {
  val namedParams = args["namedParams"] as? List<*>
                    ?: (args["params"] as? Map<*, *>)?.entries?.firstOrNull()?.value as? List<*>
                    ?: return emptyList()
  return java.util.List.copyOf(namedParams.filterIsInstance<NamedParameterDescriptor>())
}

private fun params(args: Map<*, *>): List<VariableDescriptor> {
  val params = args["params"] as? Map<*, *> ?: return emptyList()
  val result = ArrayList<VariableDescriptor>()
  var first = true
  for ((key, value) in params.entries) {
    if (!first || value !is List<*>) {
      result.add(VariableDescriptor(name = key.toString(), type = stringifyType(value)))
    }
    first = false
  }
  return java.util.List.copyOf(result)
}

private fun throws(args: Map<*, *>): List<String> {
  val throws = args["throws"]
  return when {
    throws is List<*> -> java.util.List.copyOf(throws.map(::stringifyType))
    throws != null -> listOf(stringifyType(throws))
    else -> emptyList()
  }
}

fun parseClosure(args: Map<*, *>): ClosureDescriptor? {
  val methodArgs = args["method"] as? Map<*, *> ?: return null
  val methodParameterTypes = ArrayList<String>()
  val usePlaceContext: Boolean
  when (val paramArgs: Any? = methodArgs["params"]) {
    is Map<*, *> -> {
      if (args["namedParams"] is List<*>) {
        methodParameterTypes.add(CommonClassNames.JAVA_UTIL_MAP)
        paramArgs.values.mapTo(methodParameterTypes, ::stringifyType)
      }
      else {
        var first = true
        for ((_, value) in paramArgs) {
          val isNamed = first && value is List<*>
          methodParameterTypes += if (isNamed) CommonClassNames.JAVA_UTIL_MAP else stringifyType(value)
          first = false
        }
      }
      usePlaceContext = true
    }
    is List<*> -> {
      paramArgs.mapTo(methodParameterTypes, ::stringifyType)
      usePlaceContext = false
    }
    else -> {
      return null
    }
  }

  val params = ArrayList<VariableDescriptor>()
  if (namedParams(args).isNotEmpty()) {
    params.add(VariableDescriptor("args", CommonClassNames.JAVA_UTIL_MAP))
  }
  params.addAll(params(args))

  return ClosureDescriptor(
    methodName = args["name"].toString(),
    methodParameterTypes = java.util.List.copyOf(methodParameterTypes),
    usePlaceContextForTypes = usePlaceContext,
    isMethodConstructor = methodArgs["constructor"] == true,
    parameters = java.util.List.copyOf(params)
  )
}

data class NamedParameterDescriptor(
  val name: String?,
  val type: String,
  val doc: String?,
)

fun parseNamedParameter(args: Map<*, *>): NamedParameterDescriptor {
  return NamedParameterDescriptor(
    name = args["name"] as String?,
    type = stringifyType(args["type"]),
    doc = args["doc"] as? String,
  )
}

private fun stringifyType(type: Any?): String {
  return when (type) {
    null -> CommonClassNames.JAVA_LANG_OBJECT
    is Closure<*> -> GroovyCommonClassNames.GROOVY_LANG_CLOSURE
    is Map<*, *> -> CommonClassNames.JAVA_UTIL_MAP
    is Class<*> -> type.name
    else -> {
      val s = type.toString()
      LOG.assertTrue(!s.startsWith("? extends"), s)
      LOG.assertTrue(!s.contains("?extends"), s)
      LOG.assertTrue(!s.contains("<null."), s)
      LOG.assertTrue(!s.startsWith("null."), s)
      LOG.assertTrue(!(s.contains(",") && !s.contains("<")), s)
      s
    }
  }
}
