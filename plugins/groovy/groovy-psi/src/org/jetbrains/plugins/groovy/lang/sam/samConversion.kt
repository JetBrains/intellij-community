// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.sam

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil.isTrait
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.ExplicitRuntimeTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType

fun findSingleAbstractMethod(clazz: PsiClass): PsiMethod? = findSingleAbstractSignatureCached(clazz)?.method

fun findSingleAbstractSignature(clazz: PsiClass): MethodSignature? = findSingleAbstractSignatureCached(clazz)

private fun findSingleAbstractMethodAndClass(type: PsiType): Pair<PsiMethod, PsiClassType.ClassResolveResult>? {
  val groundType = (type as? PsiClassType)?.let { FunctionalInterfaceParameterizationUtil.getNonWildcardParameterization(it) }
                   ?: return null
  val resolveResult = (groundType as PsiClassType).resolveGenerics()

  val samClass = resolveResult.element ?: return null

  val sam = findSingleAbstractMethod(samClass) ?: return null
  return sam to resolveResult
}

private fun findSingleAbstractSignatureCached(clazz: PsiClass): HierarchicalMethodSignature? {
  return CachedValuesManager.getCachedValue(clazz) {
    CachedValueProvider.Result.create(doFindSingleAbstractSignature(clazz), clazz)
  }
}

private fun doFindSingleAbstractSignature(clazz: PsiClass): HierarchicalMethodSignature? {
  var result: HierarchicalMethodSignature? = null
  for (signature in clazz.visibleSignatures) {
    if (!isEffectivelyAbstractMethod(signature)) continue
    if (result != null) return null // found another abstract method
    result = signature
  }
  return result
}

private fun isEffectivelyAbstractMethod(signature: HierarchicalMethodSignature): Boolean {
  val method = signature.method
  if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) return false
  if (isObjectMethod(signature)) return false
  if (isImplementedTraitMethod(method)) return false
  return true
}

private fun isObjectMethod(signature: HierarchicalMethodSignature): Boolean {
  return signature.superSignatures.any {
    it.method.containingClass?.qualifiedName == JAVA_LANG_OBJECT
  }
}

private fun isImplementedTraitMethod(method: PsiMethod): Boolean {
  val clazz = method.containingClass ?: return false
  if (!isTrait(clazz)) return false
  val traitMethod = method as? GrMethod ?: return false
  return traitMethod.block != null
}

fun isSamConversionAllowed(context: PsiElement): Boolean {
  return GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY2_2)
}

internal fun processSAMConversion(targetType: PsiType,
                                  closureType: GroovyClosureType,
                                  context: PsiElement): List<ConstraintFormula> {
  val constraints = mutableListOf<ConstraintFormula>()
  val pair = findSingleAbstractMethodAndClass(targetType)
  if (pair == null) {
    constraints.add(
      TypeConstraint(targetType, TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, context), context))
    return constraints
  }
  val (sam, classResolveResult) = pair

  val groundClass = classResolveResult.element ?: return constraints
  val groundType = groundTypeForClosure(sam, groundClass, closureType, context)

  if (groundType != null) {
    constraints.add(TypeConstraint(targetType, groundType, context))
  }
  return constraints
}

private fun returnTypeConstraint(samReturnType: PsiType?,
                                 returnType: PsiType?,
                                 context: PsiElement): ConstraintFormula? {
  if (returnType == null || samReturnType == null || samReturnType == PsiType.VOID) {
    return null
  }

  return TypeConstraint(samReturnType, returnType, context)
}

/**
 * JLS 18.5.3
 * com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil.getFunctionalTypeExplicit
 */
private fun groundTypeForClosure(sam: PsiMethod,
                                 groundClass: PsiClass,
                                 closureType: GroovyClosureType,
                                 context: PsiElement): PsiClassType? {
  if (!Registry.`is`("groovy.use.explicitly.typed.closure.in.inference", true)) return null

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

  val samSession = GroovyInferenceSession(typeParameters, PsiSubstitutor.EMPTY, context, false)
  argumentMapping.expectedTypes.forEach { (expectedType, argument) ->
    val leftType = samSession.substituteWithInferenceVariables(groundClassSubstitutor.substitute(expectedType))
    samSession.addConstraint(
      TypePositionConstraint(ExpectedType(leftType, GrTypeConverter.Position.METHOD_PARAMETER), argument.type, context))
  }

  val returnTypeConstraint = returnTypeConstraint(sam.returnType, closureType.returnType(arguments), context)
  if (returnTypeConstraint != null) samSession.addConstraint(returnTypeConstraint)

  if (!samSession.repeatInferencePhases()) {
    return null
  }
  val resultSubstitutor = samSession.result()

  val elementFactory = JavaPsiFacade.getElementFactory(context.project)
  return elementFactory.createType(groundClass, resultSubstitutor)
}
