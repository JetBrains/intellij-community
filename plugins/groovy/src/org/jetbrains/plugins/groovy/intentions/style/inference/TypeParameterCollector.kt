// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import java.util.*

fun acceptFinalSubstitutor(metaData: InferenceMetaData,
                           resultSubstitutor: PsiSubstitutor,
                           collector: TypeParameterCollector,
                           constantParameters: List<PsiTypeParameter>) {
  val targetParameters = metaData.parameterIndex.keys
  val gatheredTypeParameters = collectDependencies(metaData.virtualMethod.typeParameterList!!, resultSubstitutor)
  targetParameters.forEach { param ->
    param.setType(resultSubstitutor.substitute(param.type))
    when {
      isClosureType(param.type) -> {
        metaData.closureParameters[param]?.run {
          substituteTypes(resultSubstitutor, gatheredTypeParameters)
          renderTypes(metaData.virtualMethod.parameterList)
        }
      }
      param.type is PsiArrayType -> param.setType(
        resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
    }
  }
  val residualTypeParameterList = buildResidualTypeParameterList(metaData, collector, constantParameters)
  metaData.virtualMethod.typeParameterList?.replace(residualTypeParameterList)
  if (metaData.virtualMethod.typeParameters.isEmpty()) {
    metaData.virtualMethod.typeParameterList?.delete()
  }
}


private fun buildResidualTypeParameterList(metaData: InferenceMetaData,
                                           collector: TypeParameterCollector,
                                           constantParameters: List<PsiTypeParameter>): PsiTypeParameterList {
  metaData.virtualMethod.typeParameterList!!.replace(collector.typeParameterList)
  val necessaryTypeParameters = LinkedHashSet<PsiTypeParameter>()
  val visitor = object : PsiTypeVisitor<PsiType>() {

    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
      val resolvedClass = classType.resolveGenerics().element
      if (resolvedClass is PsiTypeParameter) {
        if (resolvedClass.name !in necessaryTypeParameters.map { it.name }) {
          necessaryTypeParameters.add(resolvedClass)
          resolvedClass.extendsList.referencedTypes.forEach { it.accept(this) }
        }
      }
      classType.parameters.forEach { it.accept(this) }
      return super.visitClassType(classType)
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
      wildcardType?.extendsBound?.accept(this)
      return super.visitWildcardType(wildcardType)
    }

    override fun visitArrayType(arrayType: PsiArrayType?): PsiType? {
      arrayType?.componentType?.accept(this)
      return super.visitArrayType(arrayType)
    }
  }
  metaData.virtualMethod.parameters.forEach { it.type.accept(visitor) }
  metaData.closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
  val takenNames = necessaryTypeParameters.map { it.name }
  val remainedConstantParameters = constantParameters.filter { it.name !in takenNames }
  return GroovyPsiElementFactory.getInstance(collector.project).createMethodFromText(
    "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString(", ") { it.text }}> void foo() {}").typeParameterList!!
}

class TypeParameterCollector(context: PsiElement) {
  val project = context.project
  val typeParameterList: PsiTypeParameterList = GroovyPsiElementFactory.getInstance(project).createTypeParameterList()

  fun createBoundedTypeParameter(name: String,
                                 resultSubstitutor: PsiSubstitutor,
                                 advice: PsiType): PsiTypeParameter {
    val mappedSupertypes = when {
      advice is PsiClassType && (advice.name != name) -> arrayOf(resultSubstitutor.substitute(advice) as PsiClassType)
      advice is PsiIntersectionType -> PsiIntersectionType.flatten(advice.conjuncts, mutableSetOf()).map {
        resultSubstitutor.substitute(it) as PsiClassType
      }.toTypedArray()
      else -> emptyArray()
    }
    return GroovyPsiElementFactory.getInstance(project).createProperTypeParameter(name, mappedSupertypes).apply {
      this@TypeParameterCollector.typeParameterList.add(this)
    }
  }
}


