// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


fun parameterizeMethod(metaData: InferenceMetaData, signatureSubstitutor: PsiSubstitutor): List<PsiTypeParameter> {
  metaData.virtualMethod.typeParameterList?.replace(metaData.defaultTypeParameterList.copy())
  val constantTypes = metaData.virtualMethod.typeParameters
  val parameterizer = Parameterizer(constantTypes.map { it.name!! },
                                    metaData.virtualMethod,
                                    metaData.closureParameters,
                                    metaData.varargParameters,
                                    signatureSubstitutor)
  for ((parameter, typeParameter) in metaData.parameterIndex) {
    parameter.setType(parameterizer.createDeeplyParametrizedType(signatureSubstitutor.substitute(typeParameter)!!, parameter))
  }
  return constantTypes.toList()
}


private class Parameterizer(restrictedNames: Collection<String>,
                            val virtualMethod: PsiMethod,
                            val closureParameters: Map<GrParameter, ParameterizedClosure>,
                            val varargParameters: Set<GrParameter>,
                            val signatureSubstitutor: PsiSubstitutor) {
  val nameGenerator = NameGenerator(restrictedNames)
  val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)

  private fun registerTypeParameter(vararg supertypes: PsiClassType): PsiType {
    val typeParameter =
      elementFactory.createProperTypeParameter(nameGenerator.name, supertypes.filter {
        !it.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)
      }.toTypedArray())
    virtualMethod.typeParameterList!!.add(typeParameter)
    return typeParameter.type()
  }

  /**
   * Creates type parameter with upper bound of [target].
   * If [target] is parametrized, all it's parameter types will also be parametrized.
   */
  fun createDeeplyParametrizedType(target: PsiType,
                                   parameter: GrParameter?): PsiType {
    val visitor = object : PsiTypeMapper() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val generifiedClassType = if (classType.isRaw) {
          val resolveResult = classType.resolve()!!
          val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(virtualMethod.manager) }
          elementFactory.createType(resolveResult, *wildcards)
        }
        else classType
        val mappedParameters = generifiedClassType.parameters.map { it.accept(this) }.toTypedArray()
        if (classType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return registerTypeParameter()
        }
        else {
          // we should not create new type parameter here because it may be a component of an intersection type
          return elementFactory.createType(classType.resolve() ?: return null, *mappedParameters)
        }
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
        wildcardType ?: return wildcardType
        val upperBounds = if (wildcardType.isExtends) arrayOf(wildcardType.extendsBound.accept(this) as PsiClassType) else emptyArray()
        return registerTypeParameter(*upperBounds)
      }

      override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiType? {
        intersectionType ?: return intersectionType
        val parametrizedConjuncts = intersectionType.conjuncts.map { it.accept(this) as PsiClassType }.toTypedArray()
        return registerTypeParameter(*parametrizedConjuncts)
      }

    }
    when {
      isClosureType(target) && closureParameters.containsKey(parameter) -> {
        val closureParameter = closureParameters[parameter]!!
        val initialTypeParameters = virtualMethod.typeParameterList!!.typeParameters
        closureParameter.typeParameters.run {
          forEach {
            val topLevelType = createDeeplyParametrizedType(signatureSubstitutor.substitute(it)!!, null)
            closureParameter.types.add(topLevelType)
          }
          val createdTypeParameters = virtualMethod.typeParameterList!!.typeParameters.subtract(initialTypeParameters.asIterable())
          clear()
          addAll(createdTypeParameters)
        }
        return target.accept(visitor)
      }
      target is PsiArrayType -> {
        return target.componentType.accept(visitor).createArrayType()
      }
      parameter in varargParameters -> {
        return target.accept(visitor).createArrayType()
      }
      target is PsiClassType && target.resolve() is PsiTypeParameter -> return registerTypeParameter()
      target is PsiIntersectionType -> return target.accept(visitor) as PsiClassType
      target == PsiType.getJavaLangObject(virtualMethod.manager, virtualMethod.resolveScope) -> {
        return registerTypeParameter()
      }
      else -> {
        return registerTypeParameter(target.accept(visitor) as PsiClassType)
      }
    }
  }

}
