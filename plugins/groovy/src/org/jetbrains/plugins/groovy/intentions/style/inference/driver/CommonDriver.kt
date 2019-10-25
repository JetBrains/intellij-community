// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.compose
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.ASSIGNMENT
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractMethod

class CommonDriver private constructor(private val targetParameters: Set<GrParameter>,
                                       private val varargParameter: GrParameter?,
                                       private val closureDriver: InferenceDriver,
                                       private val originalMethod: GrMethod,
                                       private val typeParameters: Collection<PsiTypeParameter>,
                                       searchScope: SearchScope? = null) : InferenceDriver {
  private val method = targetParameters.first().parentOfType<GrMethod>()!!
  private val scope: SearchScope = searchScope ?: with(originalMethod) { GlobalSearchScope.fileScope(project, containingFile.virtualFile) }
  private val calls = lazy { ReferencesSearch.search(originalMethod, scope).findAll().sortedBy { it.element.textOffset } }

  companion object {

    internal fun createDirectlyFromMethod(method: GrMethod): InferenceDriver {
      if (method.parameters.isEmpty()) {
        return EmptyDriver
      }
      else {
        return CommonDriver(method.parameters.toSet(), null, EmptyDriver, method, method.typeParameters.asList())
      }
    }

    fun createFromMethod(method: GrMethod,
                         virtualMethod: GrMethod,
                         generator: NameGenerator,
                         scope: SearchScope): InferenceDriver {
      val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
      val targetParameters = setUpParameterMapping(method, virtualMethod)
        .filter { it.key.typeElement == null }
        .map { it.value }
        .toSet()
      val typeParameters = mutableListOf<PsiTypeParameter>()
      for (parameter in targetParameters) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, null)
        typeParameters.add(newTypeParameter)
        virtualMethod.typeParameterList!!.add(newTypeParameter)
        parameter.setType(newTypeParameter.type())
      }
      val varargParameter = targetParameters.find { it.isVarArgs }
      varargParameter?.ellipsisDots?.delete()
      if (targetParameters.isEmpty()) {
        return EmptyDriver
      }
      else {
        return CommonDriver(targetParameters, varargParameter, EmptyDriver, method, typeParameters, scope)
      }
    }

    private fun GrParameter.setTypeWithoutFormatting(type: PsiType?) {
      if (type == null || type == PsiType.NULL || (type is PsiWildcardType && !type.isBounded)) {
        typeElementGroovy?.delete()
      }
      else try {
        val desiredTypeElement = GroovyPsiElementFactory.getInstance(project).createTypeElement(removeWildcard(type))
        if (typeElementGroovy == null) addAfter(desiredTypeElement, modifierList) else typeElementGroovy?.replace(desiredTypeElement)
      }
      catch (e: IncorrectOperationException) {
        typeElementGroovy?.delete()
      }
    }

  }

  override fun typeParameters(): Collection<PsiTypeParameter> {
    return typeParameters
  }


  override fun collectSignatureSubstitutor(): PsiSubstitutor {
    val mapping = setUpParameterMapping(originalMethod, method).map { it.key.name to it.value }.toMap()
    val (constraints, samParameters) = collectOuterCallsInformation()
    val inferenceSession = CollectingGroovyInferenceSession(typeParameters().toTypedArray(),
                                                            originalMethod,
                                                            proxyMethodMapping = mapping,
                                                            ignoreClosureArguments = samParameters)
    constraints.forEach { inferenceSession.addConstraint(it) }
    return inferenceSession.inferSubst()
  }

  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): InferenceDriver {
    if (varargParameter != null) {
      targetMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = setUpParameterMapping(method, targetMethod)
    val typeParameters = mutableListOf<PsiTypeParameter>()
    for (parameter in targetParameters) {
      val newParameter = parameterMapping[parameter] ?: continue
      val newType = manager.createDeeplyParameterizedType(substitutor.substitute(parameter.type).forceWildcardsAsTypeArguments())
      newType.typeParameters.forEach { targetMethod.typeParameterList!!.add(it) }
      typeParameters.addAll(newType.typeParameters)
      if (parameter == varargParameter) {
        newParameter.setType(newType.type.createArrayType())
      }
      else {
        newParameter.setType(newType.type)
      }
    }
    val copiedVirtualMethod = createVirtualMethod(targetMethod) ?: return EmptyDriver
    val closureDriver = ClosureDriver.createFromMethod(originalMethod, copiedVirtualMethod, manager.nameGenerator, scope)
    val signatureSubstitutor = closureDriver.collectSignatureSubstitutor()
    val virtualToActualSubstitutor = createVirtualToActualSubstitutor(copiedVirtualMethod, targetMethod)
    val erasureSubstitutor = RecursiveMethodAnalyzer.methodTypeParametersErasureSubstitutor(targetMethod)
    val newClosureDriver = closureDriver.createParameterizedDriver(manager, targetMethod,
                                                                   signatureSubstitutor compose (virtualToActualSubstitutor compose erasureSubstitutor))
    return CommonDriver(targetParameters.map { parameterMapping.getValue(it) }.toSet(),
                        parameterMapping[varargParameter],
                        newClosureDriver,
                        originalMethod, typeParameters, scope)
  }

  override fun collectOuterConstraints(): Collection<ConstraintFormula> {
    return collectOuterCallsInformation().first
  }


  private fun collectOuterCallsInformation(): Pair<Collection<ConstraintFormula>, Set<GrParameter>> {
    val constraintCollector = mutableListOf<ConstraintFormula>()
    for (parameter in targetParameters) {
      constraintCollector.add(ExpressionConstraint(ExpectedType(parameter.type, ASSIGNMENT), parameter.initializerGroovy ?: continue))
    }
    val candidateSamParameters = targetParameters.map { it to PsiType.NULL as PsiType }.toMap(mutableMapOf())
    val definitelySamParameters = mutableSetOf<GrParameter>()
    val mapping = setUpParameterMapping(originalMethod, method)
    for (call in calls.value.mapNotNull { it.element.parent }) {
      if (call is GrExpression) {
        constraintCollector.add(ExpressionConstraint(null, call))
        fetchSamCoercions(candidateSamParameters, definitelySamParameters, call, mapping)
      }
      else if (call is GrConstructorInvocation) {
        val resolveResult = call.constructorReference.advancedResolve()
        if (resolveResult is GroovyMethodResult) {
          constraintCollector.add(MethodCallConstraint(null, resolveResult, call))
        }
      }
    }
    method.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
        val resolveResult = methodCallExpression.advancedResolve() as? GroovyMethodResult
        val argumentMapping = resolveResult?.candidate?.argumentMapping ?: return
        for ((type, argument) in argumentMapping.expectedTypes) {
          val properType = resolveResult.substitutor.substitute(type)
          val typeParameter = (argument.type?.resolve() as? PsiTypeParameter).takeIf { it in typeParameters } ?: continue
          if (properType == typeParameter.type()) {
            continue
          }
          constraintCollector.add(TypeConstraint(properType, typeParameter.type(), method))
        }
      }
    })
    return Pair(constraintCollector, candidateSamParameters.keys.intersect(definitelySamParameters))
  }


  private fun fetchSamCoercions(samCandidates: MutableMap<GrParameter, PsiType>,
                                samParameters: MutableSet<GrParameter>,
                                call: GrExpression,
                                mapping: Map<GrParameter, GrParameter>) {
    val argumentMapping = ((call as? GrMethodCall)?.advancedResolve() as? GroovyMethodResult)?.candidate?.argumentMapping ?: return
    argumentMapping.expectedTypes.forEach { (_, argument) ->
      val virtualParameter = mapping[argumentMapping.targetParameter(argument)]?.takeIf { it in samCandidates.keys } ?: return@forEach
      val argumentType = argument.type as? PsiClassType ?: return@forEach
      if (virtualParameter in samCandidates.keys) {
        if (argument.type.isClosureTypeDeep()) {
          return@forEach
        }
        val sam = findSingleAbstractMethod(argumentType.resolve() ?: return@forEach)
        if (sam == null) {
          samCandidates.remove(virtualParameter)
        }
        else {
          samParameters.add(virtualParameter)
          if (TypesUtil.canAssign(argumentType, samCandidates[virtualParameter]!!, method, METHOD_PARAMETER) == OK) {
            samCandidates[virtualParameter] = argumentType
          }
          else if (TypesUtil.canAssign(samCandidates[virtualParameter]!!, argumentType, method, METHOD_PARAMETER) != OK) {
            samCandidates.remove(virtualParameter)
          }
        }
      }
    }
  }


  override fun collectInnerConstraints(): TypeUsageInformation {
    val typeUsageInformation = closureDriver.collectInnerConstraints()
    val analyzer = RecursiveMethodAnalyzer(method)
    analyzer.runAnalyzer(method)
    analyzer.visitOuterCalls(originalMethod, calls.value)
    return analyzer.buildUsageInformation() + typeUsageInformation
  }

  override fun instantiate(resultMethod: GrMethod) {
    if (resultMethod.parameters.last().typeElementGroovy == null) {
      resultMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = setUpParameterMapping(method, resultMethod)
    for ((virtualParameter, actualParameter) in parameterMapping) {
      val virtualType = virtualParameter.type
      val newParamType = when {
        virtualType is PsiArrayType -> {
          val component = virtualType.componentType
          (if (component is PsiWildcardType) component.bound else component)?.createArrayType()
        }
        virtualParameter.typeElement == null -> PsiType.NULL
        else -> virtualParameter.type
      }
      actualParameter.setTypeWithoutFormatting(newParamType)
    }
    closureDriver.instantiate(resultMethod)
  }

  override fun acceptTypeVisitor(visitor: PsiTypeMapper, resultMethod: GrMethod): InferenceDriver {
    val mapping = setUpParameterMapping(method, resultMethod)
    method.parameters.forEach {
      val type = if (it.typeElement == null) PsiType.NULL else it.type
      mapping.getValue(it).setTypeWithoutFormatting(type.accept(visitor))
    }
    val newClosureDriver = closureDriver.acceptTypeVisitor(visitor, resultMethod)
    val newTypeParameters = typeParameters.mapNotNull { param -> resultMethod.typeParameters.find { it.name == param.name } }
    val newTargetParameters = targetParameters.map { mapping.getValue(it) }.toSet()
    return CommonDriver(newTargetParameters, mapping[varargParameter], newClosureDriver, originalMethod, newTypeParameters)
  }
}