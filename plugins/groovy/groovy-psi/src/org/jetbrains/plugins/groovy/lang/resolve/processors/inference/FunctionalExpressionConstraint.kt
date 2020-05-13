// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil.getNonWildcardParameterization
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.ExplicitRuntimeTypeArgument
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractMethod
import org.jetbrains.plugins.groovy.lang.sam.isSamConversionAllowed
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType

class FunctionalExpressionConstraint(private val expression: GrFunctionalExpression,
                                     private val leftType: PsiType) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<in ConstraintFormula>): Boolean {
    if (leftType !is PsiClassType) return true
    val returnType by lazy(LazyThreadSafetyMode.NONE) {
      expression.returnType
    }
    if (TypesUtil.isClassType(leftType, GROOVY_LANG_CLOSURE)) {
      val parameters = leftType.parameters
      if (parameters.size != 1) return true
      if (returnType == null || returnType == PsiType.VOID) {
        return true
      }
      constraints.add(TypeConstraint(parameters[0], returnType, expression))
    }
    else {
      processSAMConversion(constraints)
    }
    return true
  }

  private fun processSAMConversion(constraints: MutableList<in ConstraintFormula>) {
    val pair = getSingleAbstractMethod()
    if (pair == null) {
      constraints.add(TypeConstraint(leftType, TypesUtil.createTypeByFQClassName(GROOVY_LANG_CLOSURE, expression), expression))
      return
    }
    val (sam, classResolveResult) = pair

    val groundClass = classResolveResult.element ?: return
    val groundType = groundTypeForExplicitlyTypedClosure(sam, groundClass)

    if (groundType != null) {
      constraints.add(TypeConstraint(leftType, groundType, expression))
    }

    val samReturnType = classResolveResult.substitutor.substitute(sam.returnType)
    if (samReturnType == null || samReturnType == PsiType.VOID) {
      return
    }
    val returnType = expression.returnType
    if (returnType == null) {
      return
    }

    constraints.add(TypeConstraint(samReturnType, returnType, expression))
  }

  private fun getSingleAbstractMethod(): Pair<PsiMethod, ClassResolveResult>? {
    if (!isSamConversionAllowed(expression)) return null
    val groundType = (leftType as? PsiClassType)?.let { getNonWildcardParameterization(it) } ?: return null
    val resolveResult = (groundType as PsiClassType).resolveGenerics()

    val samClass = resolveResult.element ?: return null

    val sam = findSingleAbstractMethod(samClass) ?: return null
    return sam to resolveResult
  }

  /**
   * JLS 18.5.3
   * com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil.getFunctionalTypeExplicit
   */
  private fun groundTypeForExplicitlyTypedClosure(sam: PsiMethod, groundClass: PsiClass): PsiClassType? {
    if (!Registry.`is`("groovy.use.explicitly.typed.closure.in.inference", true)) return null
    val closureType = expression.type as? GroovyClosureType ?: return null
    val parameters = expression.parameters
    val types = parameters.map { it.declaredType }
    if (types.filterNotNull().isEmpty()) return null // implicitly typed Closure

    val typeParameters = groundClass.typeParameters ?: return null
    if (typeParameters.isEmpty()) return null

    val samContainingClass = sam.containingClass ?: return null
    val groundClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(samContainingClass, groundClass, PsiSubstitutor.EMPTY)

    // erase all ground class parameters to null, otherwise explicit closure signature will be inapplicable
    val erasingSubstitutor = PsiSubstitutor.createSubstitutor(typeParameters.associate { it to PsiType.NULL })
    val samParameterTypes = sam.parameterList.parameters.map { it.type }
    val arguments = samParameterTypes.map {
      val withInheritance = groundClassSubstitutor.substitute(it)
      ExplicitRuntimeTypeArgument(withInheritance, TypeConversionUtil.erasure(erasingSubstitutor.substitute(withInheritance)))
    }

    val argumentMapping = closureType.applyTo(arguments).find { it.applicability() == Applicability.applicable } ?: return null

    val samSession = GroovyInferenceSession(typeParameters, PsiSubstitutor.EMPTY, expression)
    argumentMapping.expectedTypes.forEach { (expectedType, argument) ->
      val leftType = samSession.substituteWithInferenceVariables(groundClassSubstitutor.substitute(expectedType))
      samSession.addConstraint(TypePositionConstraint(ExpectedType(leftType, METHOD_PARAMETER), argument.type, expression))
    }
    if (!samSession.repeatInferencePhases()) {
      return null
    }
    val resultSubstitutor = samSession.result()

    val elementFactory = JavaPsiFacade.getElementFactory(expression.project)
    return elementFactory.createType(groundClass, resultSubstitutor)
  }
}