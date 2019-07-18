// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createVirtualMethod
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod


class InferenceDriver internal constructor(val processors: List<ParametersProcessor>,
                                           val method: GrMethod,
                                           val virtualMethod: GrMethod,
                                           val defaultTypeParameterList: PsiTypeParameterList) {
  companion object {
    fun createDriverFromMethod(method: GrMethod): InferenceDriver {
      val virtualMethod = createVirtualMethod(method)
      val defaultTypeParameterList = virtualMethod.typeParameterList!!.copy() as PsiTypeParameterList
      val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
      val closureProcessor = ClosureProcessor.createFromMethod(method, virtualMethod, generator)
      val commonProcessor = CommonProcessor.createFromMethod(method, virtualMethod, generator)
      return InferenceDriver(listOf(commonProcessor, closureProcessor), method, virtualMethod, defaultTypeParameterList)
    }

    fun createParameterizedDriver(driver: InferenceDriver, signatureSubstitutor: PsiSubstitutor): InferenceDriver {
      val parameterizationManager = ParameterizationManager(driver)
      val parameterizedMethod = createVirtualMethod(driver.method)
      val newProcessors = driver.processors.map {
        it.createParameterizedProcessor(parameterizationManager, parameterizedMethod, signatureSubstitutor)
      }
      return InferenceDriver(newProcessors, driver.method, parameterizedMethod, driver.defaultTypeParameterList)
    }

  }

  fun collectOuterConstraints(): Collection<ConstraintFormula> {
    return processors.flatMap { it.collectOuterConstraints(method) }
  }

  fun collectInnerConstraints(): TypeUsageInformation {
    return TypeUsageInformation.merge(processors.map { it.collectInnerConstraints() })
  }

  fun instantiate(resultSubstitutor: PsiSubstitutor,
                  newTypeParameters: Collection<PsiTypeParameter> = emptyList()): GrMethod {
    val resultMethod = createVirtualMethod(method)
    if (resultMethod.parameters.last().typeElementGroovy == null) {
      resultMethod.parameters.last().ellipsisDots?.delete()
    }
    processors.forEach { it.instantiate(resultMethod, resultSubstitutor) }
    val residualTypeParameterList = buildResidualTypeParameterList(newTypeParameters, resultMethod)
    resultMethod.typeParameterList!!.replace(residualTypeParameterList)
    if (resultMethod.typeParameters.isEmpty()) {
      resultMethod.typeParameterList?.delete()
    }
    return resultMethod
  }


  private fun buildResidualTypeParameterList(typeParameters: Collection<PsiTypeParameter>, resultMethod: GrMethod): PsiTypeParameterList {
    val factory = GroovyPsiElementFactory.getInstance(resultMethod.project)
    val list = factory.createTypeParameterList()
    typeParameters.forEach { list.add(it) }
    resultMethod.typeParameterList!!.replace(list)
    val necessaryTypeParameters = mutableSetOf<PsiTypeParameter>()
    val visitor = object : PsiTypeVisitor<Unit>() {

      override fun visitClassType(classType: PsiClassType?) {
        classType ?: return
        val resolvedClass = classType.resolveGenerics().element
        if (resolvedClass is PsiTypeParameter) {
          if (resolvedClass.name !in necessaryTypeParameters.map { it.name }) {
            necessaryTypeParameters.add(resolvedClass)
            resolvedClass.extendsList.referencedTypes.forEach { it.accept(this) }
          }
        }
        classType.parameters.forEach { it.accept(this) }
        super.visitClassType(classType)
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?) {
        wildcardType?.extendsBound?.accept(this)
        super.visitWildcardType(wildcardType)
      }

      override fun visitArrayType(arrayType: PsiArrayType?) {
        arrayType?.componentType?.accept(this)
        super.visitArrayType(arrayType)
      }
    }
    processors.forEach { it.acceptReducingVisitor(visitor, resultMethod) }
    val takenNames = necessaryTypeParameters.map { it.name }
    val remainedConstantParameters = defaultTypeParameterList.typeParameters.filter { it.name !in takenNames }
    return factory.createMethodFromText(
      "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString(", ") { it.text }}> void foo() {}")
      .typeParameterList!!
  }
}

