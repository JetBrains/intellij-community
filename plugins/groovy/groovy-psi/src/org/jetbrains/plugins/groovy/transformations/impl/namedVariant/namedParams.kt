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
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

const val NAMED_VARIANT_ORIGIN_INFO: String = "via @NamedVariant"
const val NAMED_ARGS_PARAMETER_NAME = "__namedArgs"
const val GROOVY_TRANSFORM_NAMED_VARIANT = "groovy.transform.NamedVariant"
const val GROOVY_TRANSFORM_NAMED_PARAM = "groovy.transform.NamedParam"
const val GROOVY_TRANSFORM_NAMED_DELEGATE = "groovy.transform.NamedDelegate"


fun collectNamedParams(mapParameter: PsiParameter): List<Pair<String, PsiType>> {
  if (!mapParameter.type.equalsToText(CommonClassNames.JAVA_UTIL_MAP)) return emptyList()

  return mapParameter.annotations.mapNotNull(::constructNamedParameter)
}

private fun constructNamedParameter(annotation: PsiAnnotation): Pair<String, PsiType>? {
  if(annotation.qualifiedName != GROOVY_TRANSFORM_NAMED_PARAM) return null
  val attributeLiteral = annotation.findAttributeValue("value")
  val name = (attributeLiteral as? GrLiteral)?.value as? String ?: return null
  val classValue = annotation.findAttributeValue("type") as? GrExpression ?: return null
  val type = ResolveUtil.getClassReferenceFromExpression(classValue) ?: return null
  return name to type
}

/**
 * The order of the parameters is preserved as it is in the code
 */
fun collectAllParamsFromNamedVariantMethod(method: GrMethod): List<Pair<String, GrParameter>> {
  val namedParams = collectNamedParamsFromNamedVariantMethod(method).groupBy { it.origin }
  return method.parameterList.parameters.flatMap { parameter ->
    namedParams[parameter]?.let {
      it.map { data -> data.name to data.origin }
    } ?: listOf(parameter.name to parameter)
  }
}

/**
 * The order of the parameters is preserved as it is in the code
 */
internal fun collectNamedParamsFromNamedVariantMethod(method: GrMethod): List<NamedParamData> {
  val result = mutableListOf<NamedParamData>()
  for (parameter in method.parameterList.parameters) {
    val name = parameter.name
    val type = parameter.type

    val namedParamsAnn = PsiImplUtil.getAnnotation(parameter, GROOVY_TRANSFORM_NAMED_PARAM)
    if (namedParamsAnn != null) {
      result.add(NamedParamData(name, type, parameter))
      continue
    }

    PsiImplUtil.getAnnotation(parameter, GROOVY_TRANSFORM_NAMED_DELEGATE) ?: continue
    val parameterClass = (type as? PsiClassType)?.resolve() ?: continue
    getProperties(parameterClass).forEach { propertyName, propertyType ->
      result.add(NamedParamData(propertyName, propertyType, parameter))
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

private fun getCodeProperties(typeDef: GrTypeDefinition): Map<String, PsiType?> {
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