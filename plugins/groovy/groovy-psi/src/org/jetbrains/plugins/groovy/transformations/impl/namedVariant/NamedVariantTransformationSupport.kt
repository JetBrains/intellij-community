// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getAnnotation
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightModifierList
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.resolve.ast.extractVisibility
import org.jetbrains.plugins.groovy.lang.resolve.ast.getVisibility
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class NamedVariantTransformationSupport : AstTransformationSupport {
  override fun applyTransformation(context: TransformationContext) {
    context.codeClass.codeMethods.forEach {
      if (!it.hasAnnotation(GROOVY_TRANSFORM_NAMED_VARIANT)) return@forEach
      val method = constructNamedMethod(it, context) ?: return@forEach
      context.addMethod(method)
    }
  }

  private fun constructNamedMethod(method: GrMethod, context: TransformationContext): GrLightMethodBuilder? {
    val parameters = mutableListOf<GrParameter>()
    val namedVariantAnnotation = method.getAnnotation(GROOVY_TRANSFORM_NAMED_VARIANT) ?: return null
    val mapType = TypesUtil.createType(JAVA_UTIL_MAP, method)
    val mapParameter = GrLightParameter(NAMED_ARGS_PARAMETER_NAME, mapType, method)
    val modifierList = GrLightModifierList(mapParameter)
    mapParameter.modifierList = modifierList
    parameters.add(mapParameter)
    val namedParams = collectNamedParamsFromNamedVariantMethod(method)
    namedParams.forEach { namedParam -> addNamedParamAnnotation(modifierList, namedParam) }

    val requiredParameters = method.parameterList.parameters
      .filter {
        getAnnotation(it, GROOVY_TRANSFORM_NAMED_PARAM) == null && getAnnotation(it, GROOVY_TRANSFORM_NAMED_DELEGATE) == null
      }
    if (requiredParameters.size != method.parameters.size) {
      parameters.addAll(requiredParameters)
    }

    return buildMethod(parameters, method, namedVariantAnnotation, context)
  }

  internal class NamedVariantGeneratedMethod(manager: PsiManager?, name: @NlsSafe String?) : GrLightMethodBuilder(manager, name)

  private fun buildMethod(parameters: List<GrParameter>,
                          method: GrMethod,
                          namedVariantAnnotation: PsiAnnotation,
                          context: TransformationContext): GrLightMethodBuilder? {
    val builder = NamedVariantGeneratedMethod(method.manager, method.name + "")
    val psiClass = method.containingClass ?: return null
    builder.containingClass = psiClass
    builder.returnType = method.returnType
    builder.navigationElement = method
    val defaultVisibility = extractVisibility(method)
    val requiredVisibility = getVisibility(namedVariantAnnotation, builder, defaultVisibility)
    builder.modifierList.addModifier(requiredVisibility.toString())
    if (context.hasModifierProperty(method.modifierList, PsiModifier.STATIC)) {
      builder.modifierList.addModifier(PsiModifier.STATIC)
    }
    builder.isConstructor = method.isConstructor
    parameters.forEach {
      builder.addParameter(it)
    }
    method.throwsList.referencedTypes.forEach {
      builder.addException(it)
    }
    builder.originInfo = NAMED_VARIANT_ORIGIN_INFO

    return builder
  }
}