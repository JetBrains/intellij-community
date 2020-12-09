// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("NamedParamsUtil")

package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.getArrayValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightModifierList
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

@NonNls const val NAMED_VARIANT_ORIGIN_INFO: String = "via @NamedVariant"
@NlsSafe const val NAMED_ARGS_PARAMETER_NAME = "__namedArgs"
const val GROOVY_TRANSFORM_NAMED_VARIANT = "groovy.transform.NamedVariant"
const val GROOVY_TRANSFORM_NAMED_PARAM = "groovy.transform.NamedParam"
const val GROOVY_TRANSFORM_NAMED_PARAMS = "groovy.transform.NamedParams"
const val GROOVY_TRANSFORM_NAMED_DELEGATE = "groovy.transform.NamedDelegate"
private val NAVIGABLE_ELEMENT: Key<PsiElement> = Key("GROOVY_NAMED_VARIANT_NAVIGATION_ELEMENT")


fun collectNamedParams(mapParameter: PsiParameter): List<NamedParamData> {
  if (!mapParameter.type.equalsToText(CommonClassNames.JAVA_UTIL_MAP)) return emptyList()

  val annotations = mapParameter
    .getAnnotation(GROOVY_TRANSFORM_NAMED_PARAMS)
    ?.findDeclaredAttributeValue("value")
    ?.getArrayValue { it as? GrAnnotation }

  if (annotations != null) {
    return annotations.mapNotNull { constructNamedParameter(it, mapParameter) }
  }

  return mapParameter.annotations.mapNotNull{ constructNamedParameter(it, mapParameter) }
}

private fun constructNamedParameter(annotation: PsiAnnotation, owner: PsiParameter): NamedParamData? {
  if(annotation.qualifiedName != GROOVY_TRANSFORM_NAMED_PARAM) return null
  val attributeLiteral = annotation.findAttributeValue("value")
  val name = (attributeLiteral as? GrLiteral)?.value as? String ?: return null
  val classValue = annotation.findAttributeValue("type") as? GrExpression ?: return null
  val type = ResolveUtil.getClassReferenceFromExpression(classValue) ?: return null
  val required = GrAnnotationUtil.inferBooleanAttribute(annotation, "required") ?: false
  val navigableElement = annotation.getUserData(NAVIGABLE_ELEMENT) ?: annotation
  return NamedParamData(name, type, owner, navigableElement, required)
}

/**
 * The order of the parameters is preserved as it is in the code
 */
fun collectAllParamsFromNamedVariantMethod(method: GrMethod): List<Pair<String, PsiParameter>> {
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
  val useAllParameters = method.parameterList.parameters.all {
    PsiImplUtil.getAnnotation(it, GROOVY_TRANSFORM_NAMED_PARAM) == null &&
    PsiImplUtil.getAnnotation(it, GROOVY_TRANSFORM_NAMED_DELEGATE) == null
  }
  if (useAllParameters) {
    val anno = method.getAnnotation(GROOVY_TRANSFORM_NAMED_VARIANT)
    return if (anno != null && GrAnnotationUtil.inferBooleanAttribute(anno, "autoDelegate") == true) {
      val parameter = method.parameters.singleOrNull() ?: return emptyList()
      getNamedParamDataFromClass(parameter.type, parameter, parameter)
    } else {
      method.parameters.map { NamedParamData(it.name, it.type, it, it, !it.isOptional) }
    }
  }
  for (parameter in method.parameterList.parameters) {
    val type = parameter.type

    val namedParamsAnn = PsiImplUtil.getAnnotation(parameter, GROOVY_TRANSFORM_NAMED_PARAM)
    if (namedParamsAnn != null) {
      val name = AnnotationUtil.getDeclaredStringAttributeValue(namedParamsAnn, "value") ?: parameter.name
      val required = GrAnnotationUtil.inferBooleanAttribute(namedParamsAnn, "required") ?: false
      result.add(NamedParamData(name, type, parameter, namedParamsAnn, required))
      continue
    }
    val psiAnnotation = PsiImplUtil.getAnnotation(parameter, GROOVY_TRANSFORM_NAMED_DELEGATE) ?: continue
    result.addAll(getNamedParamDataFromClass(type, parameter, psiAnnotation))
  }
  return result
}

fun getNamedParamDataFromClass(type : PsiType, parameter: PsiParameter, navigationElement: PsiElement) : List<NamedParamData> {
  val parameterClass = (type as? PsiClassType)?.resolve() ?: return emptyList()
  return getProperties(parameterClass).map { (propertyName, propertyType) ->
    NamedParamData(propertyName, propertyType, parameter, navigationElement)
  }
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

internal fun addNamedParamAnnotation(modifierList: GrLightModifierList, namedParam: NamedParamData) {
  modifierList.addAnnotation(GROOVY_TRANSFORM_NAMED_PARAM).let {
    it.addAttribute("type", namedParam.type?.presentableText ?: CommonClassNames.JAVA_LANG_OBJECT)
    it.addAttribute("value", "\"${namedParam.name}\"")
    it.addAttribute("required", "${namedParam.required}")
    it.putUserData(NAVIGABLE_ELEMENT, namedParam.navigationElement)
  }
}