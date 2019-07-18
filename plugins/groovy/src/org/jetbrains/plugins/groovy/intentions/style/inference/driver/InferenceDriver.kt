// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


class InferenceDriver private constructor(val targetParameters: Set<GrParameter>,
                                          val varargParameters: Set<GrParameter>,
                                          val closureParameters: Map<GrParameter, ParameterizedClosure>,
                                          val method: GrMethod,
                                          val virtualMethod: GrMethod,
                                          val defaultTypeParameterList: PsiTypeParameterList) {
  companion object {

    fun createDriverFromMethod(method: GrMethod): InferenceDriver {
      val virtualMethod = createVirtualMethod(method)
      val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
      val defaultTypeParameterList = virtualMethod.typeParameterList!!.copy() as PsiTypeParameterList
      val targetParameters = setUpParameterMapping(method, virtualMethod)
        .filter { it.key.typeElement == null }
        .map { it.value }
        .toSet()
      val closureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
      val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
      for (parameter in targetParameters) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
        virtualMethod.typeParameterList!!.add(newTypeParameter)
        parameter.setType(newTypeParameter.type())
      }

      collectClosureArguments(method, virtualMethod).forEach { (parameter, calls) ->
        // todo: default-valued parameters
        val parameterizedClosure = ParameterizedClosure(parameter)
        closureParameters[parameter] = parameterizedClosure
        repeat((calls.first() as GrClosableBlock).allParameters.size) {
          val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parameterizedClosure.typeParameters.add(newTypeParameter)
        }
      }
      val varargParameters = virtualMethod.parameters.filter { it.isVarArgs }.map { it.apply { ellipsisDots!!.delete() } }.toSet()
      return InferenceDriver(targetParameters, varargParameters,
                             closureParameters, method, virtualMethod,
                             defaultTypeParameterList)
    }

    fun createParameterizedDriver(driver: InferenceDriver, signatureSubstitutor: PsiSubstitutor): InferenceDriver {
      val parameterizationManager = ParameterizationManager(driver)
      val parameterizedMethod = createVirtualMethod(driver.method)
      if (driver.virtualMethod.parameters.last() in driver.varargParameters) {
        parameterizedMethod.parameters.last().ellipsisDots?.delete()
      }
      val parameterMapping = setUpParameterMapping(driver.virtualMethod, parameterizedMethod)
      val newClosureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()

      for (parameter in driver.targetParameters) {
        val newParameter = parameterMapping.getValue(parameter)
        if (parameter in driver.closureParameters) {
          val closureParameter = driver.closureParameters.getValue(parameter)
          val newClosureParameter = ParameterizedClosure(newParameter)
          closureParameter.typeParameters.forEach { directInnerParameter ->
            val innerParameterType = parameterizationManager.createDeeplyParametrizedType(
              signatureSubstitutor.substitute(directInnerParameter)!!)
            newClosureParameter.types.add(innerParameterType.type)
            newClosureParameter.typeParameters.addAll(innerParameterType.typeParameters)
            innerParameterType.typeParameters.forEach { parameterizedMethod.typeParameterList!!.add(it) }
          }
          newClosureParameters[newParameter] = newClosureParameter
        }
        val newType = parameterizationManager.createDeeplyParametrizedType(signatureSubstitutor.substitute(parameter.type), parameter)
        newType.typeParameters.forEach { parameterizedMethod.typeParameterList!!.add(it) }
        newParameter.setType(newType.type)
      }
      return InferenceDriver(
        driver.targetParameters.map { parameterMapping.getValue(it) }.toSet(),
        driver.varargParameters.map { parameterMapping.getValue(it) }.toSet(),
        newClosureParameters,
        driver.method,
        parameterizedMethod,
        driver.defaultTypeParameterList)
    }

  }


  fun collectOuterConstraints(): Collection<ConstraintFormula> {
    val closure = PsiType.getTypeByName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, virtualMethod.project, virtualMethod.resolveScope)
    val restoreTypeMapping = mutableMapOf<GrParameter, PsiTypeParameter>()
    val constraintCollector = mutableListOf<ConstraintFormula>()
    for (parameter in closureParameters.keys) {
      // allows to resolve Closure#call
      restoreTypeMapping[parameter] = parameter.type.typeParameter()!!
      parameter.setType(closure)
    }
    val innerUsages = virtualMethod.block
      ?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
    if (innerUsages != null) {
      closureParameters
        .keys
        .forEach {
          if (innerUsages.containsKey(it)) {
            collectClosureParametersConstraints(constraintCollector, closureParameters.getValue(it), innerUsages.getValue(it))
          }
          it.setType(restoreTypeMapping[it]!!.type())
        }
    }
    for (parameter in virtualMethod.parameters) {
      constraintCollector.add(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrExpression }) {
      constraintCollector.add(ExpressionConstraint(null, call))
    }
    return constraintCollector
  }

  fun collectInnerConstraints(): TypeUsageInformation {
    val analyzer = RecursiveMethodAnalyzer(virtualMethod, closureParameters)
    virtualMethod.accept(analyzer)
    val constraintCollector = mutableListOf<ConstraintFormula>()
    virtualMethod.block?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
      ?.forEach { (parameter, usages) ->
        if (parameter is GrParameter && parameter in closureParameters.keys) {
          collectDeepClosureDependencies(constraintCollector,
                                         closureParameters.getValue(parameter),
                                         usages)
        }
      }
    val typeInformation = analyzer.buildUsageInformation()
    return TypeUsageInformation(
      typeInformation.contravariantTypes +
      closureParameters.flatMap { it.value.types },
      typeInformation.requiredClassTypes,
      typeInformation.constraints + constraintCollector)
  }

  fun instantiate(resultSubstitutor: PsiSubstitutor,
                  newTypeParameters: Collection<PsiTypeParameter> = emptyList()): GrMethod {
    val gatheredTypeParameters = collectDependencies(virtualMethod.typeParameterList!!, resultSubstitutor)
    val resultMethod = createVirtualMethod(method)
    if (resultMethod.parameters.last().typeElementGroovy == null) {
      resultMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = virtualMethod.parameters.zip(resultMethod.parameters).toMap()
    targetParameters.forEach { param ->
      val realParameter = parameterMapping.getValue(param)
      realParameter.setType(resultSubstitutor.substitute(param.type))
      when {
        realParameter.type.isClosureType() -> {
          closureParameters[param]?.run {
            substituteTypes(resultSubstitutor, gatheredTypeParameters)
            realParameter.modifierList.addAnnotation(renderTypes(virtualMethod.parameterList).substring(1))
          }
        }
        param.type is PsiArrayType -> realParameter.setType(
          resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
      }
    }
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
    resultMethod.parameters.forEach { it.type.accept(visitor) }
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
    val takenNames = necessaryTypeParameters.map { it.name }
    val remainedConstantParameters = defaultTypeParameterList.typeParameters.filter { it.name !in takenNames }
    return factory.createMethodFromText(
      "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString(", ") { it.text }}> void foo() {}")
      .typeParameterList!!
  }


}

