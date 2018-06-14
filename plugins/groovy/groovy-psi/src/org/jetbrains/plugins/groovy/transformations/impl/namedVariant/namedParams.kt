// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("NamedParamsUtil")

package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

fun collectNamedParams(mapParameter: PsiParameter): List<Pair<String, PsiType>> {
  if (!mapParameter.type.equalsToText(CommonClassNames.JAVA_UTIL_MAP)) return emptyList()

  return mapParameter.annotations
    .filter { GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_PARAM == it.qualifiedName }
    .mapNotNull {
      val attributeLiteral = it.findAttributeValue("value")
      val name = (attributeLiteral as? GrLiteral)?.value as? String ?: return@mapNotNull null
      val classValue = it.findAttributeValue("type") as? GrExpression ?: return@mapNotNull null
      val type = ResolveUtil.getClassReferenceFromExpression(classValue) ?: return@mapNotNull null

      return@mapNotNull (name to type)
    }
}

/**
 * The order of the parameters is preserved as it is in the code
 */
fun collectAllParamsFromNamedVariantMethod(method: GrMethod): List<Pair<String, GrParameter>> {
  val namedParams = collectNamedParamsFromNamedVariantMethod(method).groupBy { it.origin }
  return method.parameterList.parameters.flatMap {
    namedParams[it]?.let {
      return@flatMap it.map { data -> data.name to data.origin }
    }
    return@flatMap listOf(it.name to it)
  }
}

/**
 * The order of the parameters is preserved as it is in the code
 */
fun collectNamedParamsFromNamedVariantMethod(method: GrMethod): List<NamedParamData> {
  val result = mutableListOf<NamedParamData>()
  method.parameterList.parameters.forEach { parameter ->
    val name = parameter.name
    val type = parameter.type

    val namedParamsAnn = PsiImplUtil.getAnnotation(parameter, GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_PARAM)
    if (namedParamsAnn != null) {
      result.add(NamedParamData(name, type, parameter))
      return@forEach
    }

    val namedDelegateAnn = PsiImplUtil.getAnnotation(parameter, GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_DELEGATE)
    if (namedDelegateAnn != null) {
      val parameterClass = (parameter.type as? PsiClassType)?.resolve() ?: return@forEach

      getProperties(parameterClass).forEach { propertyName, propertyType ->
        result.add(NamedParamData(propertyName, propertyType, parameter))
      }
      return@forEach
    }
  }
  return result
}

private fun getProperties(psiClass: PsiClass): Map<String, PsiType?> {
  if (psiClass is GrTypeDefinition) {
    return getCodeProperties(psiClass)
  }
  val allProperties = PropertyUtilBase.getAllProperties(psiClass, true, false, true)
  return allProperties
    .filter { "metaClass" != it.key }
    .map { it.key to PropertyUtilBase.getPropertyType(it.value) }
    .toMap()
}

fun getCodeProperties(typeDef: GrTypeDefinition): Map<String, PsiType?> {
  val result = mutableMapOf<String, PsiType?>()
  typeDef.codeFields.filter { it.isProperty }.forEach {
    result[it.name] = it.declaredType
  }

  typeDef.codeMethods.filter { GroovyPropertyUtils.isSimplePropertySetter(it) }.forEach {
    val name = GroovyPropertyUtils.getPropertyNameBySetter(it) ?: return@forEach
    result[name] = PropertyUtilBase.getPropertyType(it)
  }
  typeDef.getSupers(false).forEach {
    result.putAll(getProperties(it))
  }
  return result
}