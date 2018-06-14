// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightModifierList
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_PARAM
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_VARIANT
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

private const val NAMED_VARIANT_ORIGIN_INFO: String = "via @NamedVariant"
private const val NAMED_ARGS_PARAMETER_NAME = "__namedArgs"

class NamedVariantTransformationSupport : AstTransformationSupport {
  override fun applyTransformation(context: TransformationContext) {
    context.methods.filterIsInstance(GrMethod::class.java).forEach {
      val annotation = PsiImplUtil.getAnnotation(it, GROOVY_TRANSFORM_NAMED_VARIANT) ?: return@forEach
      val method = constructNamedMethod(it) ?: return@forEach
      method.navigationElement = annotation
      context.addMethod(method)
    }
  }

  private fun constructNamedMethod(method: GrMethod): GrLightMethodBuilder? {
    val parameters = mutableListOf<GrParameter>()
    val mapType = TypesUtil.createType(JAVA_UTIL_MAP, method)
    val mapParameter = GrLightParameter(NAMED_ARGS_PARAMETER_NAME, mapType, method)
    val modifierList = GrLightModifierList(mapParameter)
    mapParameter.modifierList = modifierList
    parameters.add(mapParameter)
    val namedParams = collectNamedParamsFromNamedVariantMethod(method)
    namedParams.forEach { namedParam ->
      modifierList.addAnnotation(GROOVY_TRANSFORM_NAMED_PARAM).let {
        it.addAttribute("type", namedParam.type?.presentableText ?: JAVA_LANG_OBJECT)
        it.addAttribute("value", "\"${namedParam.name}\"")
      }
    }

    val setOfProcessedParams = namedParams.map { it.origin }.toSet()
    method.parameterList.parameters.filter { !setOfProcessedParams.contains(it) }.forEach {
      parameters.add(GrLightParameter(it))
    }

    return buildMethod(parameters, method)
  }

  private fun buildMethod(parameters: List<GrParameter>, method: GrMethod): GrLightMethodBuilder? {
    val builder = GrLightMethodBuilder(method.manager, method.name + "")
    val psiClass = method.containingClass ?: return null
    builder.containingClass = psiClass
    builder.returnType = method.returnType
    builder.navigationElement = method
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